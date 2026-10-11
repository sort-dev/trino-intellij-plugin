package dev.sort.trino.brikk

import dev.brikk.house.intellij.*
import dev.brikk.house.sql.ast.*
import dev.brikk.house.sql.generator.SourceMap
import dev.brikk.house.sql.shape.SqlFragment
import dev.sort.trino.pipes.runPipeCatching
import java.util.IdentityHashMap

/** Trino editing adapter over core ASTs and copied input contracts. No project-wide SQL schema. */
object BrikkSqlScopes {
    data class Relation(val alias: String, val columns: List<ColumnContract>, val knowledge: ShapeKnowledge,
                        val managed: Boolean, val available: Boolean, val slot: String? = null,
                        val navigation: NavigationTarget? = null, val sourceStart: Int = -1)
    data class Scope(val relations: List<Relation>, val outer: Scope? = null) {
        fun relation(qualifier: String): Relation? = relations.firstOrNull { it.alias.equals(qualifier, true) } ?: outer?.relation(qualifier)
        fun candidates(name: String, qualifier: String = ""): List<Pair<Relation, ColumnContract>> {
            val selected = if (qualifier.isBlank()) relations else relations.filter { it.alias.equals(qualifier, true) }
            val own = selected.flatMap { row -> row.columns.filter { it.name.equals(name, true) }.map { row to it } }
            if (qualifier.isNotBlank() && selected.isNotEmpty() || own.isNotEmpty()) return own
            if (qualifier.isBlank() && selected.any { !it.available || it.knowledge == ShapeKnowledge.PARTIAL }) return emptyList()
            return outer?.candidates(name, qualifier).orEmpty()
        }
        val columns get() = relations.flatMap { r -> r.columns.map { r to it } }
        val managed get() = relations.any { it.managed }
        val complete get() = relations.isNotEmpty() && relations.all { it.available && it.knowledge == ShapeKnowledge.FULL }
    }
    enum class Kind { COLUMN, QUALIFIER, SLOT }
    data class Reference(val name: String, val qualifier: String, val start: Int, val end: Int, val scope: Scope, val kind: Kind = Kind.COLUMN)
    data class Analysis(val references: List<Reference>) {
        fun at(offset: Int): Reference? = references.filter { offset >= it.start && offset < it.end }.minByOrNull { it.end - it.start }
    }
    fun analyze(sql: String, contracts: Map<String, ShapeResult>): Analysis? = runPipeCatching {
        Analyzer(contracts).analyze(SqlFragment(sql, "trino").ast)
    }.getOrNull()

    private class Analyzer(private val contracts: Map<String, ShapeResult>) {
        private val references = mutableListOf<Reference>()
        private val active = java.util.Collections.newSetFromMap(IdentityHashMap<Expression, Boolean>())
        fun analyze(root: Expression): Analysis { query(root, emptyMap(), null); return Analysis(references) }
        private fun range(node: Expression) = SourceMap.sourcePosOf(node)?.takeIf { it.start >= 0 && it.end >= it.start }
        private fun reference(node: Expression, name: String, qualifier: String, scope: Scope, kind: Kind = Kind.COLUMN) {
            val span = range(node) ?: return
            references.add(Reference(name, qualifier, span.start, span.end + 1, scope, kind))
        }
        private fun mark(node: Expression?, scope: Scope, ctes: Map<String, Relation>) {
            if (node == null) return
            if (node is Select || node is PipeQuery || node is Subquery) { query(node, ctes, scope); return }
            if (node is Column) {
                (node.thisArg as? Expression)?.let { reference(it, node.name, node.table, scope) }
                (node.args["table"] as? Expression)?.let { reference(it, node.table, "", scope, Kind.QUALIFIER) }
            }
            node.iterExpressions().forEach { mark(it, scope, ctes) }
        }
        private fun unknown(alias: String, managed: Boolean = false, slot: String? = null) =
            Relation(alias, emptyList(), ShapeKnowledge.PARTIAL, managed, false, slot)
        private fun nullable(row: Relation) = row.copy(columns = row.columns.map { it.copy(nullability = ColumnNullability.NULLABLE) })
        private fun flatten(scope: Scope, alias: String = "") = Relation(alias, scope.columns.map { it.second },
            if (scope.complete) ShapeKnowledge.FULL else ShapeKnowledge.PARTIAL, scope.managed,
            scope.relations.all { it.available }, sourceStart = scope.relations.minOfOrNull { it.sourceStart } ?: -1)
        private fun source(node: Expression?, ctes: Map<String, Relation>, outer: Scope?): Relation {
            if (node == null) return unknown("")
            if (node is From) return source(node.thisArg as? Expression, ctes, outer)
            val alias = node.aliasOrName
            if (node is Subquery) return flatten(query(node.thisArg as? Expression ?: return unknown(alias), ctes, outer), alias)
            if (node is Table) {
                val call = node.thisArg as? Anonymous
                if (call != null && call.expressionsArg.isEmpty() && call.name in contracts) {
                    val shape = (contracts[call.name] as? ShapeResult.Available)?.shape
                    val row = if (shape == null) unknown(alias.ifBlank { call.name }, true, call.name)
                        else Relation(alias.ifBlank { call.name }, shape.columns, shape.knowledge, true, true, call.name, shape.navigation)
                    val located = row.copy(sourceStart = range(call)?.start ?: -1)
                    reference(call, call.name, "", Scope(listOf(located), outer), Kind.SLOT)
                    return located
                }
                return ctes.entries.firstOrNull { it.key.equals(node.name, true) }?.value?.copy(alias = alias,
                    sourceStart = (node.thisArg as? Expression)?.let { range(it)?.start } ?: -1) ?: unknown(alias)
            }
            return unknown(alias)
        }
        private fun join(scope: Scope, node: Expression, ctes: Map<String, Relation>): Scope {
            val right = source(node.thisArg as? Expression, ctes, scope.outer)
            val side = node.text("side").uppercase()
            val left = if (side in setOf("RIGHT", "FULL")) scope.relations.map(::nullable) else scope.relations
            val joined = Scope(left + if (side in setOf("LEFT", "FULL")) nullable(right) else right, scope.outer)
            mark(node.args["on"] as? Expression, joined, ctes)
            return if (node.text("kind").uppercase() in setOf("SEMI", "ANTI")) scope else joined
        }
        private fun column(node: Expression, scope: Scope): ColumnContract {
            val expression = node.projectionValue()
            val name = node.aliasOrName.ifBlank { expression.aliasOrName }
            if (expression is Column) {
                val matches = scope.candidates(expression.name, expression.table)
                if (matches.size == 1) return matches.single().second.copy(name = name)
            }
            if (expression is Count) return ColumnContract(name, null, ColumnNullability.NON_NULL, SqlFamily.WIDE_INTEGER)
            return ColumnContract(name, null, ColumnNullability.UNKNOWN, SqlFamily.UNKNOWN)
        }
        private fun additionAvailable(node: Expression, scope: Scope): Boolean {
            val expression = node.projectionValue()
            return expression !is Column || scope.candidates(expression.name, expression.table)
                .singleOrNull()?.first?.available == true
        }
        private fun project(expressions: List<Expression>, scope: Scope, ctes: Map<String, Relation>, extend: Boolean = false): Scope {
            expressions.forEach { mark(it, scope, ctes) }
            if (extend) {
                val additions = expressions.map { Addition(column(it, scope), additionAvailable(it, scope)) }
                    .filter { it.column.name.isNotBlank() }
                val retained = scope.relations.map { row -> row.copy(columns = row.columns.filterNot { old ->
                    additions.any { it.column.name.equals(old.name, true) }
                }) }
                val added = additions.groupBy(Addition::available).map { (available, columns) ->
                    Relation("", columns.map(Addition::column), ShapeKnowledge.FULL, scope.managed, available)
                }
                return Scope(retained + added, scope.outer)
            }
            if (expressions.size == 1 && expressions.single() is Star) return scope
            val columns = mutableListOf<ColumnContract>()
            var open = false
            for (expression in expressions) {
                if (expression is Star || expression is Column && expression.thisArg is Star) {
                    val selected = if (expression is Column && expression.table.isNotBlank()) listOfNotNull(scope.relation(expression.table)) else scope.relations
                    columns.addAll(selected.flatMap { it.columns })
                    open = open || selected.isEmpty() || selected.any { !it.available || it.knowledge == ShapeKnowledge.PARTIAL }
                } else {
                    val column = column(expression, scope)
                    if (column.name.isNotBlank()) columns.add(column) else open = true
                }
            }
            return Scope(listOf(Relation("", columns, if (open) ShapeKnowledge.PARTIAL else ShapeKnowledge.FULL,
                scope.managed, scope.relations.all { it.available })), scope.outer)
        }
        private fun query(node: Expression, inherited: Map<String, Relation>, outer: Scope?): Scope {
            if (!active.add(node)) return Scope(emptyList(), outer)
            try {
                if (node is Subquery) return query(node.thisArg as? Expression ?: return Scope(emptyList()), inherited, outer)
                val ctes = inherited.toMutableMap()
                (node.args["with_"] as? Expression)?.expressionsArg?.filterIsInstance<Expression>()?.forEach { cte ->
                    (cte.thisArg as? Expression)?.let { ctes[cte.alias] = flatten(query(it, ctes, null), cte.alias) }
                }
                if (node is PipeQuery) {
                    var scope = query(node.thisArg as Expression, ctes, outer)
                    for (stage in node.expressionsArg.filterIsInstance<Expression>()) {
                        scope = when (stage.key) {
                            "pipeselect" -> project(stage.expressionsArg.filterIsInstance<Expression>(), scope, ctes)
                            "pipeextend" -> project(stage.expressionsArg.filterIsInstance<Expression>(), scope, ctes, true)
                            "pipeaggregate" -> project(stage.expressionsArg.filterIsInstance<Expression>() +
                                (stage.args["group"] as? List<*>)?.filterIsInstance<Expression>().orEmpty(), scope, ctes)
                            "pipejoin" -> join(scope, stage.thisArg as Expression, ctes)
                            "pipeas" -> Scope(listOf(flatten(scope, stage.alias)), outer)
                            "pipewhere", "pipeorderby", "pipelimit", "pipeoffset", "pipedistinct" -> { mark(stage, scope, ctes); scope }
                            else -> { mark(stage, scope, ctes); Scope(listOf(unknown("", scope.managed)), outer) }
                        }
                    }
                    return scope
                }
                if (node !is Select) return Scope(emptyList(), outer)
                var input = Scope(node.args["from_"]?.let { listOf(source(it as Expression, ctes, outer)) }.orEmpty(), outer)
                (node.args["joins"] as? List<*>)?.filterIsInstance<Expression>()?.forEach { input = join(input, it, ctes) }
                val output = project(node.expressionsArg.filterIsInstance<Expression>(), input, ctes)
                for (key in listOf("where", "group", "having", "qualify")) mark(node.args[key] as? Expression, input, ctes)
                val aliases = output.columns.filter { input.candidates(it.second.name).isEmpty() }.map { it.second }
                mark(node.args["order"] as? Expression, if (aliases.isEmpty()) input else Scope(input.relations +
                    Relation("", aliases, ShapeKnowledge.FULL, input.managed, true), outer), ctes)
                return output
            } finally { active.remove(node) }
        }
    }
}

private data class Addition(val column: ColumnContract, val available: Boolean)

private tailrec fun Expression.projectionValue(): Expression {
    if (this !is Alias && this !is Paren) return this
    val inner = thisArg as? Expression ?: return this
    return inner.projectionValue()
}

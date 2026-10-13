package dev.sort.trino.brikk

import com.intellij.codeInsight.completion.*
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.brikk.house.intellij.ColumnNullability
import dev.brikk.house.intellij.NavigationKind
import dev.brikk.house.sql.intellij.SqlColumnCompletion
import dev.brikk.house.sql.intellij.SqlColumnFacts

class BrikkSqlCompletionContributor : CompletionContributor() {
    init { extend(CompletionType.BASIC, PlatformPatterns.psiElement(), Provider) }

    private object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val file = parameters.originalFile
            if (BrikkTrinoProfile.forFile(file) == null) return
            val manager = InjectedLanguageManager.getInstance(file.project)
            val text = manager.getUnescapedText(file)
            val offset = manager.mapInjectedOffsetToUnescaped(file, parameters.offset).coerceIn(0, text.length)
            val analysis = BrikkSqlScopes.analyze(text.substring(0, offset) + "__brikk_caret" + text.substring(offset), BrikkTrinoProfile.contracts(file))
            val ref = analysis?.at(offset)
            if (ref?.kind != BrikkSqlScopes.Kind.COLUMN) return
            val rows = if (ref.qualifier.isBlank()) ref.scope.relations else listOfNotNull(ref.scope.relation(ref.qualifier))
            rows.filter { it.managed }.flatMap { row -> row.columns.map { row to it } }
                .distinctBy { it.first.alias to it.second.name }.forEach { (row, column) ->
                    val nullable = when (column.nullability) {
                        ColumnNullability.NON_NULL -> false
                        ColumnNullability.NULLABLE -> true
                        ColumnNullability.UNKNOWN -> null
                    }
                    val origin = if (column.navigation?.kind == NavigationKind.RELATION_FALLBACK) "relation contract" else "brikk column"
                    result.caseInsensitive().addElement(SqlColumnCompletion.create(SqlColumnFacts(
                        column.name, column.kotlinClassId?.substringAfterLast('.'), nullable, row.alias, origin,
                        source = row.alias.takeIf { it.isNotBlank() })))
                }
        }
    }
}

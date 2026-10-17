package dev.sort.trino.sql

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dialects.postgres.model.PgRoot
import com.intellij.database.model.ModelFactory
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.sql.dialects.SqlResolveMappings
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlReferenceExpression
import dev.brikk.house.sql.intellij.catalog.CatalogScopes
import dev.brikk.house.sql.intellij.testing.HeldIntrospections
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.parser.SqlScriptSnapshot
import dev.sort.trino.TrinoDbms
import dev.sort.trino.pipes.TrinoPipesSettings
import io.trino.sql.parser.SqlParser
import io.trino.sql.tree.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Explicitly gated, read-only SQL scan. No embedded source data, default path or connections. */
class ExternalSqlScanTest : BasePlatformTestCase() {
    private var source: LocalDataSource? = null
    private var previousPipes = false
    private var previousVeto: String? = null
    private lateinit var held: HeldIntrospections

    override fun setUp() {
        super.setUp()
        previousPipes = project.service<TrinoPipesSettings>().enabled
        previousVeto = System.getProperty("trino.pipes")
        System.clearProperty("trino.pipes")
        held = HeldIntrospections(testRootDisposable)
    }

    override fun tearDown() = RunAll(
        { held.finishAll() },
        { project.service<TrinoPipesSettings>().enabled = previousPipes },
        { source?.let(LocalDataSourceManager.getInstance(project)::removeDataSource) },
        { SqlResolveMappings.getInstance(project).setMapping(null, null) },
        { DbPsiFacade.getInstance(project).clearCaches() },
        { previousVeto?.let { System.setProperty("trino.pipes", it) } ?: System.clearProperty("trino.pipes") },
        { super.tearDown() },
    ).run()

    private data class Probe(val id: Int, val file: Int, val line: Int, val sql: String,
        val namespace: List<String>, val nativeAccepted: Boolean)
    private data class Table(val path: List<String>, val columns: List<String>, val view: Boolean = false)

    fun testScanSelectedFiles() {
        val directory = System.getProperty("realSql.dir") ?: return
        val glob = requireNotNull(System.getProperty("realSql.glob")) { "An explicit file glob is required" }
        val output = Path.of(requireNotNull(System.getProperty("realSql.output")))
        Files.createDirectories(output)
        val root = Path.of(directory)
        val matcher = root.fileSystem.getPathMatcher("glob:$glob")
        val selected = Files.walk(root).use { paths -> paths.filter { file ->
            Files.isRegularFile(file) && file.fileName.toString().endsWith(".sql", true) &&
                (matcher.matches(root.relativize(file)) || matcher.matches(file.fileName))
        }.sorted().toList() }
        val files = (selected + listOfNotNull(System.getProperty("realSql.extraFile")?.let(Path::of)))
            .map { it.toAbsolutePath().normalize() }.distinct()
        assertFalse("The explicit glob selected no SQL files", files.isEmpty())
        val parser = SqlParser()
        val probes = ArrayList<Probe>()
        val tables = LinkedHashMap<List<String>, Table>()
        val namespaces = LinkedHashSet<List<String>>()
        for ((index, file) in files.withIndex()) {
            val text = Files.readString(file)
            var namespace = listOf("scan_catalog", "scan_schema")
            for (range in SqlScriptSnapshot.parse(text, "trino").statements.filter { it.hasSql && it.end > it.start }) {
                val sql = text.substring(range.start, range.end).trim()
                val node = try { parser.createStatement(sql.removeSuffix(";").trimEnd()) } catch (_: io.trino.sql.parser.ParsingException) { null }
                if (node is Use) namespace = listOf(node.catalog.map { it.value }.orElse(namespace[0]), node.schema.value)
                fun qualified(parts: List<String>) = when (parts.size) {
                    1 -> namespace + parts
                    2 -> listOf(namespace[0]) + parts
                    else -> parts
                }
                val table = when (node) {
                    is CreateTable -> Table(qualified(node.name.parts), node.elements.filterIsInstance<ColumnDefinition>().map { it.name.suffix })
                    is CreateTableAsSelect -> Table(qualified(node.name.parts), node.columnAliases.orElse(emptyList()).map { it.value })
                    is CreateView -> Table(qualified(node.name.parts), projectionNames(node.query), true)
                    is CreateMaterializedView -> Table(qualified(node.name.parts), projectionNames(node.query), true)
                    else -> null
                }
                table?.let { tables[it.path] = it; namespaces += it.path.dropLast(1) }
                if (node is CreateSchema) namespaces += qualified(node.schemaName.parts + "scan_placeholder").dropLast(1)
                val line = text.substring(0, range.start).count { it == '\n' } + 1
                probes += Probe(probes.size + 1, index + 1, line, sql, namespace, node != null)
            }
        }
        Files.writeString(output.resolve("statements.json"), Json { prettyPrint = true }.encodeToString(JsonElement.serializer(),
            JsonArray(probes.map { probe -> buildJsonObject {
                put("id", probe.id); put("file", probe.file); put("line", probe.line); put("sql", probe.sql)
                put("namespace", JsonArray(probe.namespace.map(::JsonPrimitive))); put("nativeAccepted", probe.nativeAccepted)
            } })))
        Files.writeString(output.resolve("model.json"), Json.encodeToString(JsonElement.serializer(), JsonArray(tables.values.map { table ->
            buildJsonObject { put("path", JsonArray(table.path.map(::JsonPrimitive))); put("columns", JsonArray(table.columns.map(::JsonPrimitive))) }
        })))
        myFixture.enableInspections(com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools()
            .single { it.shortName == "SqlResolveInspection" }.tool)
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
        val findings = ArrayList<JsonObject>()
        var exceptions = 0
        for (model in listOf(true, false)) {
            if (model) source = dataSource(tables.values.toList(), namespaces)
            for (pipes in listOf(false, true)) {
                project.service<TrinoPipesSettings>().enabled = pipes
                settle()
                for (probe in probes) {
                    try {
                        val file = myFixture.configureByText("external-scan.sql", probe.sql)
                        val console = source?.let { RecordingConsole(project, file.virtualFile, it).also { recording ->
                            recording.console.setSearchPath(SearchPath.of(ObjectPath.create(probe.namespace[0], ObjectKind.DATABASE)
                                .append(probe.namespace[1], ObjectKind.SCHEMA)))
                        } }
                        try {
                            settle()
                            for (info in myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING }) {
                                findings += finding(probe, model, pipes, info)
                            }
                        } finally { console?.close(); held.finishAll() }
                    } catch (failure: Throwable) {
                        exceptions++
                        findings += buildJsonObject {
                            put("id", probe.id); put("model", model); put("pipes", pipes)
                            put("exception", failure.javaClass.name); put("description", failure.stackTraceToString())
                        }
                    }
                }
            }
            source?.let(LocalDataSourceManager.getInstance(project)::removeDataSource)
            source = null
            DbPsiFacade.getInstance(project).clearCaches()
        }
        Files.writeString(output.resolve("findings.json"), Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), JsonArray(findings)))
        println("External SQL scan: files=${files.size}, statements=${probes.size}, DDL relations=${tables.size}, checks=${probes.size * 4}, findings=${findings.size}, exceptions=$exceptions")
        assertEquals("External scan exceptions; private diagnostics are in the configured scratch directory", 0, exceptions)
    }

    private fun settle() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun finding(probe: Probe, model: Boolean, pipes: Boolean, info: HighlightInfo) = buildJsonObject {
        put("id", probe.id); put("model", model); put("pipes", pipes); put("nativeAccepted", probe.nativeAccepted)
        put("layer", info.inspectionToolId ?: "annotator-or-parser"); put("severity", info.severity.name)
        put("start", info.startOffset); put("end", info.endOffset); put("description", info.description.orEmpty())
        put("flagged", myFixture.editor.document.getText(TextRange(info.startOffset, info.endOffset)))
        val reference = PsiTreeUtil.getNonStrictParentOfType(myFixture.file.findElementAt(info.startOffset), SqlReferenceExpression::class.java)
        put("targetKind", reference?.referenceElementType?.targetKind?.name().orEmpty())
        var owner = reference
        while (owner?.parent is SqlReferenceExpression) owner = owner.parent as SqlReferenceExpression
        put("owningKind", owner?.referenceElementType?.targetKind?.name().orEmpty())
        put("container", owner?.parent?.javaClass?.simpleName.orEmpty())
        val core = dev.brikk.house.sql.scope.RelationScope.analyze(probe.sql, "trino", dev.brikk.house.sql.scope.RelationFacts())
        put("coreReferences", core.references.size)
        put("coreProblems", JsonArray(core.diagnostics.map { JsonPrimitive(it.problem.name) }))
    }

    private fun dataSource(tables: List<Table>, namespaces: Set<List<String>>): LocalDataSource {
        val model = ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK)
        val root = model.root as PgRoot
        for (path in namespaces) root.databases.createOrGet(path[0]).schemas.createOrGet(path[1])
        for (table in tables) {
            val schema = root.databases.createOrGet(table.path[0]).schemas.createOrGet(table.path[1])
            if (table.view) table.columns.forEach { schema.views.createOrGet(table.path[2]).columns.createOrGet(it) }
            else table.columns.forEach { schema.tables.createOrGet(table.path[2]).columns.createOrGet(it) }
            if (table.columns.isEmpty()) {
                if (table.view) schema.views.createOrGet(table.path[2]) else schema.tables.createOrGet(table.path[2])
            }
        }
        return LocalDataSource().apply {
            name = "Offline external SQL scan"
            databaseDriver = requireNotNull(DatabaseDriverManager.getInstance().getDriver("trino-brikk"))
            url = "jdbc:offline:external-scan"; isAutoSynchronize = false; isKeepAlive = false
            this.model = model
            if (namespaces.isNotEmpty()) introspectionScope = namespaces
                .map { CatalogScopes.select(listOf(ObjectKind.DATABASE, ObjectKind.SCHEMA), it) }.reduce(CatalogScopes::widen)
        }.also {
            LocalDataSourceManager.getInstance(project).addDataSource(it)
            DbPsiFacade.getInstance(project).clearCaches()
            settle()
        }
    }

    private fun projectionNames(query: Query): List<String> {
        val items = (query.queryBody as? QuerySpecification)?.select?.selectItems ?: return emptyList()
        // A mixed star projection is not a complete schema. A partial list must not become a
        // FULL physical model and manufacture missing-column reports during the scan.
        if (items.any { it is AllColumns }) return emptyList()
        val names = items.filterIsInstance<SingleColumn>().mapNotNull { column ->
            column.alias.map { it.value }.orElseGet { when (val expression = column.expression) {
                is Identifier -> expression.value
                is DereferenceExpression -> expression.field.map { it.value }.orElse(null)
                else -> null
            } }
        }
        return names.takeIf { it.size == items.size }.orEmpty()
    }
}

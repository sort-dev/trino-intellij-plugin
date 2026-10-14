package dev.sort.trino.sql

import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dialects.postgres.model.PgRoot
import com.intellij.database.model.ModelFactory
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.catalog.CatalogScopes
import dev.brikk.house.sql.intellij.testing.*
import dev.sort.trino.TrinoDbms
import java.util.UUID

/** Everyday SQL through product registrations, real model resolution and editor highlighting. */
class TrinoRealSqlContractTest : BasePlatformTestCase() {
    private var source: LocalDataSource? = null
    private var console: RecordingConsole? = null
    private lateinit var errors: BackgroundErrors

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
    }

    override fun tearDown() = RunAll(
        { console?.close() },
        { source?.let(LocalDataSourceManager.getInstance(project)::removeDataSource) },
        { DbPsiFacade.getInstance(project).clearCaches() },
        { errors.assertNone() },
        { super.tearDown() },
    ).run()

    private fun install(content: RealSqlModel) {
        console?.close()
        console = null
        source?.let(LocalDataSourceManager.getInstance(project)::removeDataSource)
        val model = ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK)
        val root = model.root as PgRoot
        fun table(path: String) = path.split('.').let { (catalog, schema, name) ->
            root.databases.createOrGet(catalog).schemas.createOrGet(schema).tables.createOrGet(name)
        }
        for ((path, columns) in content.tables) columns.forEach { table(path).columns.createOrGet(it) }
        content.bare.forEach(::table)
        for (path in content.namespaces) {
            val parts = path.split('.')
            val catalog = root.databases.createOrGet(parts[0])
            parts.getOrNull(1)?.let(catalog.schemas::createOrGet)
        }
        val namespaces = ((content.tables.keys + content.bare).map { it.split('.').dropLast(1) } +
            content.namespaces.map { it.split('.') }).distinct()
        val dataSource = LocalDataSource().apply {
            name = "Trino real SQL contract"
            databaseDriver = requireNotNull(DatabaseDriverManager.getInstance().getDriver("trino-brikk"))
            url = "jdbc:offline:real-sql"
            isAutoSynchronize = false
            this.model = model
            introspectionScope = namespaces.map { CatalogScopes.select(listOf(ObjectKind.DATABASE, ObjectKind.SCHEMA), it) }
                .reduce(CatalogScopes::widen)
        }
        source = dataSource
        LocalDataSourceManager.getInstance(project).addDataSource(dataSource)
        DbPsiFacade.getInstance(project).clearCaches()
    }

    fun testEverydaySqlThroughTheTrinoEditor() {
        install(RealSqlModel.STANDARD)
        enableResolveInspection()
        val target = object : RealSqlTarget {
            override val dialect = "trino"
            override val fixture get() = myFixture
            override fun install(model: RealSqlModel) = this@TrinoRealSqlContractTest.install(model)
            override val features = setOf(
                RealSqlFeature.FULL_JOIN, RealSqlFeature.INTERSECT_EXCEPT, RealSqlFeature.NAMED_WINDOW,
                RealSqlFeature.UNNEST, RealSqlFeature.LATERAL_SUBQUERY, RealSqlFeature.VALUES_COLUMN_ALIASES,
                RealSqlFeature.CREATE_TABLE_AS, RealSqlFeature.THREE_PART_NAMES, RealSqlFeature.DOUBLE_QUOTED_IDENTIFIERS,
            )
            override fun open(sql: String) = this@TrinoRealSqlContractTest.open(sql)
        }
        println("Trino real SQL contract: ${RealSqlCases.ALL.count { target.features.containsAll(it.needs) }}/${RealSqlCases.ALL.size} applicable cases")
        RealSqlContract(target).check()
        RealSqlContract(target).checkModelStates()
        println("Trino external-model contract: ${RealSqlModel.STATES.size} states, ${RealSqlCases.EXTERNAL.size} cases per state")
    }

    private fun enableResolveInspection() {
        val resolve = com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools()
            .single { it.shortName == "SqlResolveInspection" }.tool
        myFixture.enableInspections(resolve)
    }

    private fun open(sql: String) {
        console?.close()
        val file = myFixture.configureByText("real-sql-${UUID.randomUUID()}.sql", sql).virtualFile
        setSqlDialectMapping(project, file, TrinoSqlDialect, testRootDisposable)
        myFixture.configureFromExistingVirtualFile(file)
        console = RecordingConsole(project, file, requireNotNull(source)).also {
            it.console.setSearchPath(SearchPath.of(ObjectPath.create("shop", ObjectKind.DATABASE).append("sales", ObjectKind.SCHEMA)))
        }
    }

    fun testKnownColumnAndTableMissesAreStillReported() {
        install(RealSqlModel.STANDARD)
        enableResolveInspection()
        for ((sql, missing) in listOf("SELECT definitely_missing FROM shop.sales.orders" to "definitely_missing",
            "SELECT * FROM shop.sales.ghost" to "ghost")) {
            open(sql)
            val reports = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.WARNING)
            assertTrue("$sql: ${reports.map { it.description }}", reports.any {
                myFixture.editor.document.getText(com.intellij.openapi.util.TextRange(it.startOffset, it.endOffset)) == missing
            })
        }
    }
}

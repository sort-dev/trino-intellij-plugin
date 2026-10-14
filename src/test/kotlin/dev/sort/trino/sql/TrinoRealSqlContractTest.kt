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

    fun testEverydaySqlThroughTheTrinoEditor() {
        val model = ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK)
        val root = model.root as PgRoot
        fun table(path: String) = path.split('.').let { (catalog, schema, name) ->
            root.databases.createOrGet(catalog).schemas.createOrGet(schema).tables.createOrGet(name)
        }
        for ((path, columns) in RealSqlModel.STANDARD.tables) columns.forEach { table(path).columns.createOrGet(it) }
        RealSqlModel.STANDARD.bare.forEach(::table)
        val namespaces = (RealSqlModel.STANDARD.tables.keys + RealSqlModel.STANDARD.bare)
            .map { it.split('.').dropLast(1) }.distinct()
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
        val resolve = com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools()
            .single { it.shortName == "SqlResolveInspection" }.tool
        myFixture.enableInspections(resolve)
        val target = object : RealSqlTarget {
            override val dialect = "trino"
            override val fixture get() = myFixture
            override val features = setOf(
                RealSqlFeature.FULL_JOIN, RealSqlFeature.INTERSECT_EXCEPT, RealSqlFeature.NAMED_WINDOW,
                RealSqlFeature.UNNEST, RealSqlFeature.LATERAL_SUBQUERY, RealSqlFeature.VALUES_COLUMN_ALIASES,
                RealSqlFeature.CREATE_TABLE_AS, RealSqlFeature.THREE_PART_NAMES, RealSqlFeature.DOUBLE_QUOTED_IDENTIFIERS,
            )
            override fun open(sql: String) {
                console?.close()
                val file = myFixture.configureByText("real-sql-${UUID.randomUUID()}.sql", sql).virtualFile
                setSqlDialectMapping(project, file, TrinoSqlDialect, testRootDisposable)
                myFixture.configureFromExistingVirtualFile(file)
                console = RecordingConsole(project, file, dataSource).also {
                    it.console.setSearchPath(SearchPath.of(ObjectPath.create("shop", ObjectKind.DATABASE).append("sales", ObjectKind.SCHEMA)))
                }
            }
        }
        println("Trino real SQL contract: ${RealSqlCases.ALL.count { target.features.containsAll(it.needs) }}/${RealSqlCases.ALL.size} applicable cases")
        RealSqlContract(target).check()
    }
}

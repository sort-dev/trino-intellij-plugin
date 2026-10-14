package dev.sort.trino.sql

import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dialects.postgres.model.PgRoot
import com.intellij.database.model.ModelFactory
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.catalog.CatalogScopes
import dev.brikk.house.sql.intellij.testing.HeldIntrospections
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.verify.VerifyResult
import dev.sort.trino.TrinoDbms
import dev.sort.trino.pipes.TrinoPipesSettings
import dev.sort.trino.validate.TrinoNativeValidation

/** Invented names and neutral literals only. Missing metadata must not cause false column errors. */
class AnonymousSqlRegressionTest : BasePlatformTestCase() {
    private var source: LocalDataSource? = null
    private var previousPipes = false
    private lateinit var held: HeldIntrospections

    override fun setUp() {
        super.setUp()
        previousPipes = project.service<TrinoPipesSettings>().enabled
        held = HeldIntrospections(testRootDisposable)
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
        myFixture.enableInspections(com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools()
            .single { it.shortName == "SqlResolveInspection" }.tool)
    }

    override fun tearDown() = RunAll(
        { held.finishAll() },
        { project.service<TrinoPipesSettings>().enabled = previousPipes },
        { source?.let(LocalDataSourceManager.getInstance(project)::removeDataSource) },
        { DbPsiFacade.getInstance(project).clearCaches() },
        { super.tearDown() },
    ).run()

    fun testUnaliasedExternalTableQualifierWithoutColumns() = checkShape("""
        SELECT demo_relation_b.demo_key_a
        FROM demo_catalog_b.demo_schema_b.demo_relation_b
        WHERE demo_relation_b.demo_key_a > 2
    """.trimIndent())

    fun testNativeBuiltinCallsAreNotUnknownFunctions() = checkShape("""
        SELECT from_unixtime(7), to_unixtime(TIMESTAMP '2001-01-01 00:00:00'),
               json_extract_scalar(json_parse('{"demo_key_a":7}'), '$.demo_key_a'),
               IF(TRUE, 7, 9), TRY(7 / 0)
    """.trimIndent())

    fun testNativeCastTypesAreNotUnknownObjects() = checkShape("""
        SELECT CAST(7 AS TINYINT), CAST(7 AS DOUBLE), CAST('{}' AS JSON),
               CAST(map(ARRAY['neutral'], ARRAY[JSON '{}']) AS MAP(VARCHAR, JSON))
    """.trimIndent())

    fun testUnknownRowColumnDereference() = checkShape("""
        SELECT demo_record_a.demo_member_a, count(*)
        FROM demo_catalog_b.demo_schema_b.demo_relation_b
        GROUP BY 1
    """.trimIndent())

    fun testComputedCaseColumnsThroughCteChain() = checkShape("""
        WITH demo_step_a AS (
            SELECT *, round(demo_metric_a / 7) AS demo_bucket_a
            FROM demo_catalog_b.demo_schema_b.demo_relation_b
        ), demo_step_b AS (
            SELECT *, CASE WHEN demo_bucket_a > 2 THEN 'neutral_a' ELSE 'neutral_b' END AS demo_tag_a
            FROM demo_step_a
        ), demo_step_c AS (
            SELECT *, CASE WHEN demo_tag_a = 'neutral_a' THEN 7 ELSE 9 END AS demo_score_a
            FROM demo_step_b
        ), demo_step_d AS (
            SELECT demo_tag_a, count(*) AS demo_count_a, sum(demo_score_a) AS demo_sum_a
            FROM demo_step_c GROUP BY demo_tag_a
        )
        SELECT *, CASE demo_tag_a WHEN 'neutral_a' THEN 7 ELSE 9 END AS demo_order_a
        FROM demo_step_d WHERE demo_tag_a = 'neutral_a' ORDER BY demo_order_a
    """.trimIndent())

    fun testNonReservedKeywordRelationAlias() = checkShape("""
        WITH demo_step_a AS (
            SELECT demo_key_a FROM demo_catalog_b.demo_schema_b.demo_relation_b
        )
        SELECT demo_key_a, count(*) FROM demo_step_a offset
        GROUP BY demo_key_a
    """.trimIndent())

    fun testNonReservedFetchRelationAlias() = checkShape("""
        WITH demo_step_a AS (
            SELECT demo_key_a FROM demo_catalog_b.demo_schema_b.demo_relation_b
        )
        SELECT demo_key_a, count(*) FROM demo_step_a fetch
        GROUP BY demo_key_a
    """.trimIndent())

    fun testNativeKeywordAliasesOutsideCoreGrammar() {
        val vocabulary = io.trino.grammar.sql.SqlBaseLexer.VOCABULARY
        var checked = 0
        for (token in 1..vocabulary.maxTokenType) {
            val alias = vocabulary.getLiteralName(token)?.trim('\'') ?: continue
            if (!alias.matches(Regex("[A-Z_]+"))) continue
            val sql = "WITH demo_step_a AS (SELECT demo_key_a FROM demo_catalog_b.demo_schema_b.demo_relation_b) " +
                "SELECT demo_key_a, count(*) FROM demo_step_a $alias GROUP BY demo_key_a"
            if (TrinoNativeValidation.verify(sql) != VerifyResult.Accepted) continue
            if (dev.brikk.house.sql.scope.RelationScope.analyze(sql, "trino", dev.brikk.house.sql.scope.RelationFacts()).references.isNotEmpty()) continue
            checkShape(sql)
            checked++
        }
        assertTrue("Public native grammar must exercise an opaque alias shape", checked > 0)
        println("Anonymous native grammar replay: $checked opaque alias shapes")
    }

    fun testUnknownFunctionsTypesAliasesAndKnownColumnMissesStayReported() {
        source = model(knownColumns = true)
        for ((sql, missing) in listOf(
            "SELECT demo_absent_function_a(7)" to "demo_absent_function_a",
            "SELECT CAST(7 AS demo_absent_type_a)" to "demo_absent_type_a",
            "SELECT demo_absent_column_a FROM demo_catalog_a.demo_schema_a.demo_relation_a" to "demo_absent_column_a",
            "SELECT demo_bad_alias_a.demo_key_a FROM demo_catalog_a.demo_schema_a.demo_relation_a" to "demo_bad_alias_a",
        )) {
            val file = myFixture.configureByText("anonymous-negative.sql", sql)
            RecordingConsole(project, file.virtualFile, requireNotNull(source)).use {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                IndexingTestUtil.waitUntilIndexesAreReady(project)
                assertTrue("A real missing name was hidden", myFixture.doHighlighting(HighlightSeverity.WARNING).any { info ->
                    myFixture.editor.document.getText(com.intellij.openapi.util.TextRange(info.startOffset, info.endOffset)) == missing
                })
            }
        }
    }

    private fun checkShape(sql: String) {
        assertEquals(VerifyResult.Accepted, TrinoNativeValidation.verify(sql))
        for (withModel in listOf(true, false)) {
            if (withModel) source = model()
            for (pipes in listOf(false, true)) {
                project.service<TrinoPipesSettings>().enabled = pipes
                val file = myFixture.configureByText("anonymous-shape.sql", sql)
                val console = source?.let { RecordingConsole(project, file.virtualFile, it) }
                try {
                    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                    IndexingTestUtil.waitUntilIndexesAreReady(project)
                    val reports = myFixture.doHighlighting(HighlightSeverity.WARNING)
                    for (info in reports) {
                        var reference = PsiTreeUtil.getNonStrictParentOfType(file.findElementAt(info.startOffset), SqlReferenceExpression::class.java)
                        while (reference?.parent is SqlReferenceExpression) reference = reference.parent as SqlReferenceExpression
                        val kind = reference?.referenceElementType?.targetKind
                        assertTrue("Unexpected report, model=$withModel pipes=$pipes: ${info.description}",
                            info.inspectionToolId == "SqlResolveInspection" && kind in setOf(ObjectKind.TABLE, ObjectKind.ROUTINE))
                    }
                } finally { console?.close(); held.finishAll() }
            }
            source?.let(LocalDataSourceManager.getInstance(project)::removeDataSource)
            source = null
            DbPsiFacade.getInstance(project).clearCaches()
        }
    }

    private fun model(knownColumns: Boolean = false): LocalDataSource {
        val model = ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK)
        val schema = (model.root as PgRoot).databases.createOrGet("demo_catalog_a").schemas.createOrGet("demo_schema_a")
        val table = schema.tables.createOrGet("demo_relation_a")
        if (knownColumns) table.columns.createOrGet("demo_key_a")
        return LocalDataSource().apply {
            name = "Anonymous SQL shape model"
            databaseDriver = requireNotNull(DatabaseDriverManager.getInstance().getDriver("trino-brikk"))
            url = "jdbc:offline:anonymous-shapes"; isAutoSynchronize = false; isKeepAlive = false
            this.model = model
            introspectionScope = CatalogScopes.select(listOf(ObjectKind.DATABASE, ObjectKind.SCHEMA), listOf("demo_catalog_a", "demo_schema_a"))
        }.also {
            LocalDataSourceManager.getInstance(project).addDataSource(it)
            DbPsiFacade.getInstance(project).clearCaches()
        }
    }
}

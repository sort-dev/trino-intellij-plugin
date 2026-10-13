package dev.sort.trino.sql

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.relation.ExternalRelationFacts
import dev.brikk.house.sql.intellij.relation.RelationFactsResult
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.scope.*
import dev.sort.trino.brikk.TrinoRelationProfile

/** Copied catalog facts drive the registered editor contributors, without a Kotlin host. */
class TrinoPlainSqlSmokeTest : BasePlatformTestCase() {
    private lateinit var errors: BackgroundErrors

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
        val settings = CodeInsightSettings.getInstance()
        val previous = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        Disposer.register(testRootDisposable) { settings.AUTOCOMPLETE_ON_CODE_COMPLETION = previous }
        val facts = RelationFacts(tables = mapOf(
            "hive.web.orders" to RelationFact(listOf(
                ColumnFact("order_id", "BIGINT", null, Nullability.NON_NULL, ColumnOrigin.PHYSICAL_TABLE),
                ColumnFact("amount", "DOUBLE", null, Nullability.NULLABLE, ColumnOrigin.PHYSICAL_TABLE),
            ), Knowledge.FULL),
            "iceberg.web.orders" to RelationFact(listOf(
                ColumnFact("foreign_id", "BIGINT", null, Nullability.NON_NULL, ColumnOrigin.PHYSICAL_TABLE),
            ), Knowledge.FULL),
        ), searchPath = listOf("hive.web"))
        TrinoRelationProfile.externalFacts.point.registerExtension(ExternalRelationFacts.of {
            if (it.name.startsWith("plain-smoke")) RelationFactsResult.Available(facts, 1) else null
        }, testRootDisposable)
    }

    override fun tearDown() = RunAll(
        { LookupManager.getInstance(project).hideActiveLookup() },
        { errors.assertNone() },
        { super.tearDown() },
    ).run()

    fun testThreePartAndPartlyQualifiedColumnCompletion() {
        for (table in listOf("hive.web.orders", "web.orders", "orders")) {
            myFixture.configureByText("plain-smoke.sql", "SELECT o.<caret> FROM $table AS o")
            myFixture.completeBasic()
            val names = myFixture.lookupElementStrings.orEmpty()
            assertTrue("$table: $names", names.containsAll(listOf("order_id", "amount")))
            assertFalse("$table must use the current catalog", "foreign_id" in names)
            LookupManager.getInstance(project).hideActiveLookup()
        }
    }

    fun testKotlinAbsentStartupHasOnlyKotlinFreeExtensions() {
        if (System.getProperty("test.kotlin") != "absent") return
        assertNull(com.intellij.lang.Language.findLanguageByID("kotlin"))
        assertTrue(TrinoRelationProfile.externalFacts.extensionList.none {
            it.javaClass.name.startsWith("dev.sort.trino.brikk.kotlin.")
        })
        val guards = com.intellij.openapi.extensions.ExtensionPointName.create<dev.sort.trino.brikk.TrinoInspectionHostGuard>(
            "dev.sort.trino-intellij-plugin.inspectionHostGuard")
        assertEmpty(guards.extensionList)
        myFixture.configureByText("plain-smoke.sql", "SELECT abs(-1)")
        assertTrue(myFixture.doHighlighting().none { it.severity == HighlightSeverity.ERROR })
        errors.assertNone()
    }

    fun testRealisticTrinoHighlightingHasNoFalseErrors() {
        val statements = listOf(
            """
                WITH totals AS (
                    SELECT order_id, sum(amount) AS total
                    FROM hive.web.orders
                    GROUP BY order_id
                )
                SELECT order_id, total, row_number() OVER (ORDER BY total DESC) AS rn
                FROM totals WHERE total > 10 ORDER BY total DESC LIMIT 20
            """.trimIndent(),
            "SELECT transform(ARRAY[1, 2, 3], x -> x + 1), CAST(1 AS BIGINT), CURRENT_TIMESTAMP",
            "USE hive.web; SET SESSION query_max_run_time = '2h'; SHOW CATALOGS;",
        )
        for (sql in statements) {
            myFixture.configureByText("plain-smoke.sql", sql)
            val red = myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
            assertTrue("$sql: ${red.map { it.description }}", red.isEmpty())
        }
    }
}

package dev.sort.trino.sql

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.scope.RelationFacts
import dev.brikk.house.sql.scope.RelationScope
import dev.brikk.house.sql.verify.VerifyResult
import dev.sort.trino.validate.TrinoNativeValidation
import io.trino.sql.parser.SqlParser

/** New grammar forms must survive native validation and registered editor analysis. */
class Trino484CompatibilityTest : BasePlatformTestCase() {
    private lateinit var errors: BackgroundErrors

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
        val settings = CodeInsightSettings.getInstance()
        val previous = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        Disposer.register(testRootDisposable) { settings.AUTOCOMPLETE_ON_CODE_COMPLETION = previous }
    }

    override fun tearDown() {
        try {
            errors.assertNone()
        } finally {
            super.tearDown()
        }
    }

    private fun checkSql(sql: String) {
        SqlParser().createStatement(sql)
        assertEquals(sql, VerifyResult.Accepted, TrinoNativeValidation.verify(sql))
        myFixture.configureByText("trino484.sql", sql)
        val red = myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
        assertTrue("$sql: ${red.map { it.description }}", red.isEmpty())
    }

    fun testJsonConstructor() = checkSql("SELECT JSON('{\"a\":1}')")
    fun testJsonSerialize() = checkSql("SELECT JSON_SERIALIZE(JSON '{\"a\":1}' RETURNING VARCHAR)")
    fun testJsonScalar() = checkSql("SELECT json_scalar(1)")
    fun testJsonInputToValue() = checkSql("SELECT JSON_VALUE(JSON '{\"a\":1}', 'lax $.a' RETURNING BIGINT)")
    fun testJsonInputToQuery() = checkSql("SELECT JSON_QUERY(JSON '{\"a\":[1]}', 'lax $.a' RETURNING JSON)")
    fun testJsonInputToExists() = checkSql("SELECT JSON_EXISTS(JSON '{\"a\":1}', 'lax $.a')")
    fun testJsonInputToTable() = checkSql("SELECT * FROM JSON_TABLE(JSON '[{\"a\":1}]', 'lax $[*]' COLUMNS (a BIGINT PATH 'lax $.a'))")
    fun testIntervalLeadingPrecision() = checkSql("SELECT INTERVAL '123' DAY(3)")
    fun testIntervalFractionalPrecision() = checkSql("SELECT INTERVAL '1.123456789012' SECOND(2, 12)")
    fun testIntervalType() = checkSql("SELECT CAST(NULL AS INTERVAL DAY(3) TO SECOND(12))")
    fun testDatetimeSubtraction() = checkSql("SELECT (TIMESTAMP '2026-10-29 12:00:01' - TIMESTAMP '2026-10-28 12:00:00') DAY(3) TO SECOND(12)")

    fun testIntervalScopePreservesPrecision() {
        val sql = "WITH t AS (SELECT CAST(NULL AS INTERVAL DAY(3) TO SECOND(12)) AS duration) SELECT duration FROM t"
        val scope = RelationScope.analyze(sql, "trino", RelationFacts())
        assertTrue(scope.parsed)
        assertEquals("INTERVAL DAY(3) TO SECOND(12)", scope.output!!.columns.single().sqlType)
    }

    fun testWindowExclusions() {
        for (exclusion in listOf("CURRENT ROW", "GROUP", "TIES", "NO OTHERS")) {
            checkSql("SELECT sum(x) OVER (ORDER BY x ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW EXCLUDE $exclusion) FROM (VALUES 1, 2) AS t(x)")
        }
    }

    fun testCreateViewIfNotExists() = checkSql("CREATE VIEW IF NOT EXISTS hive.default.example_view AS SELECT 1 AS n")
    fun testCommentOnMaterializedView() = checkSql("COMMENT ON MATERIALIZED VIEW hive.default.example_view IS 'example'")

    fun testJsonScalarCompletion() {
        myFixture.configureByText("trino484.sql", "SELECT json_sca<caret>")
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings.orEmpty().toString(), "json_scalar" in myFixture.lookupElementStrings.orEmpty())
    }

    fun testJsonSerializeCompletion() {
        myFixture.configureByText("trino484.sql", "SELECT JSON_SER<caret>")
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings.orEmpty().toString(),
            myFixture.lookupElementStrings.orEmpty().any { it.equals("JSON_SERIALIZE", true) })
    }

    fun testJsonSerializeIsReserved() {
        assertTrue(TrinoNativeValidation.verify("SELECT 1 AS JSON_SERIALIZE") != VerifyResult.Accepted)
        checkSql("SELECT 1 AS \"JSON_SERIALIZE\"")
    }
}

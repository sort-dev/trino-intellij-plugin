package dev.sort.trino.sql

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.trino.TrinoDbms

/** The seed contract: the dialect registers, maps, and parses everyday Trino SQL with zero errors. */
class TrinoDialectBootTest : BasePlatformTestCase() {

    private var counter = 0

    private fun parse(sql: String): com.intellij.psi.PsiFile {
        val psi = myFixture.configureByText("b${counter++}.sql", sql)
        SqlDialectMappings.getInstance(project).setMapping(psi.virtualFile, TrinoSqlDialect.INSTANCE)
        return com.intellij.psi.PsiManager.getInstance(project).findFile(psi.virtualFile)!!
    }

    fun testDialectRegistersWithOwnDbms() {
        assertEquals("TRINO_BRIKK", TrinoDbms.TRINO_BRIKK.name)
        assertEquals(TrinoDbms.TRINO_BRIKK, TrinoSqlDialect.INSTANCE.dbms)
        assertEquals("TrinoBrikk", TrinoSqlDialect.INSTANCE.id)
    }

    fun testEverydayTrinoParsesClean() {
        val file = parse(
            """
            SELECT id, name, count(*) AS n
            FROM hive.web.users
            WHERE created_at >= DATE '2026-01-01'
            GROUP BY GROUPING SETS ((id), (id, name))
            HAVING count(*) > 1
            ORDER BY n DESC
            FETCH FIRST 10 ROWS ONLY;
            """.trimIndent(),
        )
        assertTrue(file.language.isKindOf(TrinoSqlDialect.INSTANCE))
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("everyday Trino must parse clean, got: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    fun testWindowFunctionsAndCtesParse() {
        val file = parse(
            """
            WITH ranked AS (
                SELECT user_id, amount,
                       row_number() OVER (PARTITION BY user_id ORDER BY amount DESC) AS rn
                FROM orders
            )
            SELECT * FROM ranked WHERE rn = 1;
            """.trimIndent(),
        )
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("window+CTE must parse clean on the PG base, got: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    fun testLenientHeadsHoldStatementBoundaries() {
        // Trino-only heads become ONE statement each — run-block boundaries stay correct even
        // before inner structure exists.
        val file = parse(
            """
            USE hive.web;
            SHOW CATALOGS;
            DESCRIBE hive.web.users;
            SET SESSION query_max_run_time = '2h';
            SELECT 1;
            """.trimIndent(),
        )
        val statements = PsiTreeUtil.findChildrenOfType(file, SqlStatement::class.java)
            .filter { it.parent === file }
        assertEquals("5 top-level statements expected, got ${statements.map { it.text.take(20) }}", 5, statements.size)
    }
}

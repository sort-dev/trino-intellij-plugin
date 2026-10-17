package dev.sort.trino.sql

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import com.intellij.sql.psi.SqlSelectStatement
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tier-1 contract ([TrinoPsiParser] + [TrinoLexer]): Trino-only statements parse as ONE clean
 * SQL_STATEMENT each (correct run-block boundaries, zero error elements) — including SQL routines
 * whose bodies carry `;` INSIDE the statement — and the dispatch gates never steal statements the
 * PG base parses fine (structured inner nodes must survive).
 */
class TrinoStatementBoundaryTest : BasePlatformTestCase() {

    private var counter = 0

    private fun file(sql: String): com.intellij.psi.PsiFile {
        val psi = myFixture.configureByText("s${counter++}.sql", sql)
        setSqlDialectMapping(project, psi.virtualFile, TrinoSqlDialect)
        return com.intellij.psi.PsiManager.getInstance(project).findFile(psi.virtualFile)!!
    }

    /** [sql] must parse as exactly [n] top-level statements with zero PsiErrorElements. */
    private fun assertCleanStatements(sql: String, n: Int) {
        val f = file(sql)
        val errors = PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java)
        assertTrue("expected no parse errors for: $sql\ngot: ${errors.map { it.errorDescription }}", errors.isEmpty())
        val statements = PsiTreeUtil.findChildrenOfType(f, SqlStatement::class.java)
            .filterNot { it.parent is SqlStatement }
        assertEquals("statement count for: $sql", n, statements.size)
    }

    /** THE routine-boundary case: `;` inside BEGIN...END must not end the statement. */
    fun testRoutineBodySemicolonsHoldBoundaries() = assertCleanStatements(
        """
        CREATE FUNCTION fib(n bigint)
        RETURNS bigint
        BEGIN
          DECLARE a bigint DEFAULT 1;
          IF n <= 2 THEN
            RETURN 1;
          END IF;
          WHILE n > 2 DO
            SET n = n - 1;
          END WHILE;
          RETURN a;
        END;
        SELECT 1;
        """.trimIndent(),
        2,
    )

    fun testWithFunctionInlineRoutineIsOneStatement() = assertCleanStatements(
        "WITH\nFUNCTION doubleup(x integer)\nRETURNS integer\nRETURN x * 2\nSELECT doubleup(21);\nSELECT 2;",
        2,
    )

    fun testSessionAndCatalogHeads() = assertCleanStatements(
        "USE hive.web;\nSHOW CATALOGS;\nDENY SELECT ON orders TO ROLE PUBLIC;\n" +
            "SET TIME ZONE 'America/Los_Angeles';\nPREPARE q FROM SELECT * FROM nation;\n" +
            "EXECUTE q USING 1, 2;\nEXECUTE IMMEDIATE 'SELECT 1';",
        7,
    )

    fun testTrinoDdlForms() = assertCleanStatements(
        "CREATE OR REPLACE TABLE t WITH (format = 'PARQUET') AS SELECT 1 AS x;\n" +
            "ALTER TABLE t EXECUTE optimize(file_size_threshold => '10MB');\n" +
            "ALTER TABLE t SET PROPERTIES format_version = 2;\n" +
            "CREATE BRANCH audit IN TABLE orders;\n" +
            "ANALYZE orders WITH (partitions = ARRAY[ARRAY['2026']]);",
        5,
    )

    fun testMasksKeepStructuredParse() {
        // Masked spans (time travel, MATCH_RECOGNIZE, PIVOT, @branch) leave a STRUCTURED PG
        // statement behind — the SELECT node must exist, not a flat lenient wrap.
        val f = file(
            "SELECT * FROM orders FOR TIMESTAMP AS OF TIMESTAMP '2026-01-01 00:00:00';\n" +
                "SELECT * FROM orders MATCH_RECOGNIZE (PARTITION BY custkey ORDER BY orderdate " +
                "MEASURES A.totalprice AS starting_price PATTERN (A B+) DEFINE B AS totalprice < PREV(totalprice));\n" +
                "UPDATE purchases @ audit SET status = 'x';",
        )
        assertTrue(
            "no parse errors expected",
            PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java).isEmpty(),
        )
        assertEquals(
            "masked statements keep their real SELECT structure",
            2,
            PsiTreeUtil.findChildrenOfType(f, SqlSelectStatement::class.java).count { it.parent === f },
        )
    }

    fun testGatesNeverStealPgParseableForms() {
        // Each of these is PG-grammar-parseable and must keep STRUCTURE (inner select present),
        // not fall into a lenient wrap: the doris COPY-exemption lesson.
        val f = file(
            "EXPLAIN ANALYZE SELECT * FROM t;\n" +
                "CREATE FUNCTION meaning_of_life() RETURNS bigint RETURN 42;\n" +
                "INSERT INTO t VALUES (1, 'a');\n" +
                "SET search_path = public;",
        )
        assertTrue(
            "no parse errors expected",
            PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java).isEmpty(),
        )
        assertTrue(
            "EXPLAIN ANALYZE keeps its inner structured SELECT",
            PsiTreeUtil.findChildrenOfType(f, SqlSelectStatement::class.java).isNotEmpty(),
        )
    }

    fun testBareValuesAndParenthesizedRowsStayDistinct() {
        assertCleanStatements("VALUES 1, 2; INSERT INTO t VALUES 3, 4; WITH t AS (VALUES 5) SELECT * FROM t;", 3)
        val f = file("WITH t AS (VALUES (1)) SELECT * FROM t; INSERT INTO t VALUES (2);")
        assertTrue(PsiTreeUtil.findChildrenOfType(f, SqlSelectStatement::class.java).isNotEmpty())
        assertEmpty(PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java))
    }

    fun testViewHeaderScanStopsAtAs() {
        assertCleanStatements("CREATE VIEW v COMMENT 'view' AS SELECT 1; SELECT 2;", 2)
        val f = file("CREATE VIEW v AS SELECT security FROM t;")
        assertTrue(PsiTreeUtil.findChildrenOfType(f, com.intellij.sql.psi.SqlReferenceExpression::class.java).any { it.text == "security" })
        assertEmpty(PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java))
    }

    fun testDispatchScansStopAtStatementBoundary() {
        val f = file("EXPLAIN SELECT 1; SHOW CATALOGS; CREATE VIEW v AS SELECT 2; SET SESSION x = 1;")
        assertEquals(1, PsiTreeUtil.findChildrenOfType(f, SqlSelectStatement::class.java).size)
        assertEquals(4, PsiTreeUtil.findChildrenOfType(f, SqlStatement::class.java).count { it.parent === f })
        assertEmpty(PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java))
    }
}

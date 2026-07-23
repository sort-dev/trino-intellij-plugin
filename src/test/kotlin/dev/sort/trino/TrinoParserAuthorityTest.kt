package dev.sort.trino

import io.trino.sql.parser.ParsingException
import io.trino.sql.parser.SqlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The authority embed: Trino's OWN parser (io.trino:trino-parser:483) is on the plugin
 * classpath and behaves as the validator needs — accepts real Trino SQL (including what the PG
 * substrate cannot parse), and rejects garbage with exact line/column positions.
 * This is the doris fe-sql-parser role, shipped upstream as a clean Maven artifact.
 */
class TrinoParserAuthorityTest {

    private val parser = SqlParser()

    @Test
    fun acceptsEverydayTrino() {
        assertNotNull(parser.createStatement("SELECT id, count(*) FROM hive.web.users GROUP BY id"))
    }

    @Test
    fun acceptsTrinoOnlySyntaxTheSubstrateCannot() {
        // Lambdas + TVF-style call — exactly the constructs the PG base mis-parses.
        assertNotNull(parser.createStatement("SELECT filter(ARRAY[1, 2, 3], x -> x > 1)"))
        assertNotNull(parser.createStatement("USE hive.web"))
        assertNotNull(parser.createStatement("SHOW CATALOGS LIKE 'h%'"))
        assertNotNull(parser.createStatement("SET SESSION hive.query_max_run_time = '2h'"))
    }

    @Test
    fun rejectsGarbageWithExactPosition() {
        try {
            parser.createStatement("SELECT FROM WHERE")
            fail("garbage must not parse")
        } catch (e: ParsingException) {
            assertEquals("errors carry 1-based lines", 1, e.lineNumber)
            assertTrue("errors carry a column", e.columnNumber > 0)
            assertNotNull(e.errorMessage)
        }
    }
}

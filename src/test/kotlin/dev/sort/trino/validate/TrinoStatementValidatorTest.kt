package dev.sort.trino.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of the parse authority + position arithmetic — no IDE fixture, no Application.
 * Proves engine truth (Trino-only syntax the PG substrate mis-parses is ACCEPTED; garbage is
 * rejected with a 1-based position) and the statement-offset -> document-offset mapping the
 * annotator relies on, including multi-line statements and out-of-range carets.
 */
class TrinoStatementValidatorTest {

    // --- parse authority ---

    @Test
    fun acceptsEverydayTrino() {
        assertNull(TrinoStatementValidator.parse("SELECT id, count(*) FROM hive.web.users GROUP BY id"))
        assertNull(TrinoStatementValidator.parse("WITH r AS (SELECT 1 AS n) SELECT n FROM r"))
    }

    @Test
    fun acceptsTrinoOnlySyntaxTheSubstrateCannot() {
        // The whole point of the engine authority: constructs the PG base mis-parses, Trino accepts.
        assertNull(TrinoStatementValidator.parse("SELECT filter(ARRAY[1, 2, 3], x -> x > 1)"))
        assertNull(TrinoStatementValidator.parse("USE hive.web"))
        assertNull(TrinoStatementValidator.parse("SET SESSION hive.query_max_run_time = '2h'"))
    }

    @Test
    fun flagsGarbageWithOneBasedPosition() {
        val err = TrinoStatementValidator.parse("SELECT FROM WHERE")
        assertNotNull("garbage must be flagged", err)
        assertEquals("Trino lines are 1-based", 1, err!!.line)
        assertTrue("Trino columns are 1-based (> 0)", err.column > 0)
        assertTrue("carries the engine's own words: ${err.message}", err.message.isNotBlank())
    }

    // --- position mapping (statement offset + 1-based line/col -> absolute document offset) ---

    @Test
    fun mapsColumnOnASingleLine() {
        // "SELECT FROM WHERE": column 8 (1-based) is the 'F' of FROM at index 7.
        assertEquals(7, TrinoStatementValidator.offsetOf("SELECT FROM WHERE", 0, 1, 8))
        // the same statement starting at document offset 200 shifts by exactly 200.
        assertEquals(207, TrinoStatementValidator.offsetOf("SELECT FROM WHERE", 200, 1, 8))
    }

    @Test
    fun mapsLineAndColumnAcrossMultipleLines() {
        val text = "SELECT a\nFROM t\nWHERE AND b"     // line 3 begins at index 16; 'AND' at 22
        assertEquals(9, TrinoStatementValidator.offsetOf(text, 0, 2, 1))  // right after the first '\n'
        assertEquals(16, TrinoStatementValidator.offsetOf(text, 0, 3, 1))
        assertEquals(22, TrinoStatementValidator.offsetOf(text, 0, 3, 7))
        assertEquals(1022, TrinoStatementValidator.offsetOf(text, 1000, 3, 7))
    }

    @Test
    fun clampsCaretBeyondTheStatementText() {
        // A line / column past the end clamps to the end of the text, never beyond it.
        assertEquals(3, TrinoStatementValidator.offsetOf("abc", 0, 9, 9))
        assertEquals(13, TrinoStatementValidator.offsetOf("abc", 10, 1, 100))
        // line 1, column 1 is always the statement start.
        assertEquals(50, TrinoStatementValidator.offsetOf("anything", 50, 1, 1))
    }
}

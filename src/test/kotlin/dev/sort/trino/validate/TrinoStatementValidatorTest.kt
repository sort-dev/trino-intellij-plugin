package dev.sort.trino.validate

import dev.brikk.house.sql.intellij.pipe.NativeVerdict
import dev.brikk.house.sql.verify.VerifyError
import dev.brikk.house.sql.verify.VerifyResult
import org.junit.Test
import kotlin.test.*

class TrinoStatementValidatorTest {
    @Test fun acceptsEverydayTrino() {
        assertSame(VerifyResult.Accepted, TrinoNativeValidation.verify("SELECT id, count(*) FROM hive.web.users GROUP BY id"))
        assertSame(VerifyResult.Accepted, TrinoNativeValidation.verify("WITH r AS (SELECT 1 AS n) SELECT n FROM r"))
    }
    @Test fun acceptsTrinoOnlySyntaxTheSubstrateCannot() {
        for (sql in listOf("SELECT filter(ARRAY[1, 2, 3], x -> x > 1)", "USE hive.web", "SET SESSION hive.query_max_run_time = '2h'")) {
            assertSame(VerifyResult.Accepted, TrinoNativeValidation.verify(sql))
        }
    }
    @Test fun flagsGarbageWithOneBasedPosition() {
        val error = assertIs<VerifyResult.Rejected>(TrinoNativeValidation.verify("SELECT FROM WHERE")).error
        assertEquals(1, error.line)
        assertEquals(8, error.column)
        assertEquals(7, error.offset)
        assertTrue(error.message.isNotBlank())
    }
    @Test fun mapsColumnOnASingleLine() {
        val offset = VerifyError.at("SELECT FROM WHERE", "bad", 1, 8).offset!!
        assertEquals(7, offset)
        assertEquals(207, 200 + offset)
    }
    @Test fun mapsLineAndColumnAcrossMultipleLines() {
        val text = "SELECT a\nFROM t\nWHERE AND b"
        assertEquals(9, VerifyError.at(text, "bad", 2, 1).offset)
        assertEquals(16, VerifyError.at(text, "bad", 3, 1).offset)
        assertEquals(22, VerifyError.at(text, "bad", 3, 7).offset)
        assertEquals(1022, 1000 + VerifyError.at(text, "bad", 3, 7).offset!!)
    }
    @Test fun positionsBeyondTheStatementAreUnknown() {
        assertNull(VerifyError.at("abc", "bad", 9, 9).offset)
        assertNull(VerifyError.at("abc", "bad", 1, 100).offset)
        assertEquals(50, 50 + VerifyError.at("anything", "bad", 1, 1).offset!!)
    }
    @Test fun supplementaryCharactersAndCrLfKeepUtf16Positions() {
        for (sql in listOf("SELECT '😀' AS emoji FROM t WHERE AND x", "SELECT '😀' AS emoji\r\nFROM t\r\nWHERE AND x")) {
            val error = assertIs<VerifyResult.Rejected>(TrinoNativeValidation.verify(sql)).error
            val offset = sql.indexOf("AND")
            assertEquals(offset, error.offset)
            assertEquals(sql.take(offset).count { it == '\n' } + 1, error.line)
            assertEquals(offset - sql.lastIndexOf('\n', offset - 1), error.column)
        }
    }
    @Test fun unverifiedAndUnavailableAreNeverAcceptedOrAnnotated() {
        for (result in listOf(VerifyResult.Unverified("unsupported channel"), VerifyResult.Unavailable("offline"))) {
            assertSame(NativeVerdict.Unavailable, result.toNativeVerdict())
            assertTrue(result.nativeErrors().isEmpty())
        }
        assertSame(NativeVerdict.Accepted, VerifyResult.Accepted.toNativeVerdict())
    }
    @Test fun rejectionsPreserveErrorsAndBlockNativeExecution() {
        val errors = listOf(VerifyError.atOffset("SELECT 1", "first", 0, 6), VerifyError("second"))
        val result = VerifyResult.Rejected(errors)
        assertEquals(errors, result.nativeErrors())
        assertEquals("first\nsecond", assertIs<NativeVerdict.Rejected>(result.toNativeVerdict()).message)
    }
}

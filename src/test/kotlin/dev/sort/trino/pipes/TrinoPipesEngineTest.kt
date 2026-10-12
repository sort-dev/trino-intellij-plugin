package dev.sort.trino.pipes

import dev.brikk.house.sql.intellij.pipe.NativeVerdict
import dev.brikk.house.sql.intellij.pipe.PipeTranslation
import dev.brikk.house.sql.intellij.pipe.ValidationTiming
import dev.brikk.house.sql.intellij.pipe.containsPipe
import dev.brikk.house.sql.intellij.pipe.lower
import dev.brikk.house.sql.parser.ColumnUnit
import dev.sort.trino.validate.TrinoStatementValidator
import org.junit.Test
import kotlin.test.*

class TrinoPipesEngineTest {
    @Test fun nativeValidatorUsesTrinoAtLowering() {
        assertEquals(ValidationTiming.Lowering, TrinoPipeDialect.validator.timing)
        assertSame(NativeVerdict.Accepted, TrinoPipeDialect.validator.validate("SELECT 1", null))
        assertIs<NativeVerdict.Rejected>(TrinoPipeDialect.validator.validate("SELECT FROM WHERE", null))
        assertEquals(ColumnUnit.CODE_POINT, TrinoPipeDialect.serverColumnUnit)
        assertEquals(1, TrinoPipeDialect.serverFirstColumn)
    }
    @Test fun lowersNestedAndTopLevelPipesToNativeTrino() {
        for (sql in listOf(
            "FROM orders |> WHERE status = 'open' |> AGGREGATE count(*) AS n GROUP BY region |> ORDER BY n DESC |> LIMIT 10",
            "FROM orders |> EXTEND price * 2 AS doubled |> SELECT doubled |> WHERE doubled > 10",
            "SELECT * FROM (FROM orders |> SELECT id) AS p",
            "FROM orders |> JOIN other ON orders.id = other.id |> SELECT orders.id |> DISTINCT",
        )) {
            val result = assertIs<PipeTranslation.Lowered>(TrinoPipeDialect.lower(sql))
            assertNull(result.blocker, "$sql: ${result.blocker}")
            assertSame(NativeVerdict.Accepted, result.verdict)
            assertFalse(TrinoPipeDialect.containsPipe(result.sql))
            assertNull(TrinoStatementValidator.parse(result.sql))
            assertNotNull(result.result.sourceMap)
        }
    }
    @Test fun lexicalOwnershipAndRoutineBoundaries() {
        for (sql in listOf("SELECT '|>'", "SELECT 1 -- |> hi", "SELECT \"|>\" FROM t", "SELECT 'broken |> hidden"))
            assertFalse(TrinoPipeDialect.containsPipe(sql), sql)
        val sql = "CREATE FUNCTION f() RETURNS bigint BEGIN RETURN 1; END; FROM t |> LIMIT 1; SELECT 2;"
        val chunks = TrinoPipeDialect.scriptRanges(sql)
        assertEquals(3, chunks.size)
        assertTrue(sql.substring(chunks[0].start, chunks[0].end).contains("RETURN 1; END;"))
        assertTrue(chunks[1].hasPipe)
        assertNotNull(TrinoPipeDialect.scriptRanges("FROM t |> WHERE x = 'bad; SELECT 2").single().boundaryError)
        assertNotNull(TrinoPipeDialect.scriptRanges("FROM t |> SELECT id /* unfinished").single().boundaryError)
        assertIs<PipeTranslation.Refused>(TrinoPipeDialect.lower("SELECT 'broken |> hidden"))
    }
    @Test fun rejectedStatementsAndParameterValidation() {
        assertIs<PipeTranslation.Refused>(TrinoPipeDialect.lower("FROM t |> BOGUS 2"))
        assertIs<PipeTranslation.Refused>(TrinoPipeDialect.lower("FROM t |> WHERE x = 'bad"))
        assertTrue(TrinoPipeDialect.containsPipe("SHOW TABLES |> WHERE x = 1"))
        assertIs<PipeTranslation.Refused>(TrinoPipeDialect.lower("SHOW TABLES |> WHERE x = 1"))
        val sql = "\n FROM t |> WHERE id = :id |> SELECT id"
        val param = TrinoPipeDialect.parameterSpans(sql).single()
        assertEquals(":id", sql.substring(param.start, param.end))
        assertNull(assertIs<PipeTranslation.Lowered>(TrinoPipeDialect.lower(sql)).blocker)
        assertNotNull(assertIs<PipeTranslation.Lowered>(TrinoPipeDialect.lower(sql, substituted = true)).blocker)
        assertNull(assertIs<PipeTranslation.Lowered>(TrinoPipeDialect.lower(sql.replace(":id", "17"), substituted = true)).blocker)
    }
    @Test fun initialStageIsScopedToAClaimedPipeline() {
        assertIs<PipeTranslation.Lowered>(TrinoPipeDialect.lower("FROM t", firstStage = true))
        assertIs<PipeTranslation.Lowered>(TrinoPipeDialect.lower("SELECT 1 AS id", firstStage = true))
    }
}

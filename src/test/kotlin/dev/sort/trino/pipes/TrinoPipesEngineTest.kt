package dev.sort.trino.pipes

import dev.brikk.house.sql.intellij.SqlExecutionRefusal
import dev.sort.trino.validate.TrinoStatementValidator
import org.junit.Test
import kotlin.test.*

class TrinoPipesEngineTest {
    @Test fun lowersNestedAndTopLevelPipesToNativeTrino() {
        for (sql in listOf(
            "FROM orders |> WHERE status = 'open' |> AGGREGATE count(*) AS n GROUP BY region |> ORDER BY n DESC |> LIMIT 10",
            "FROM orders |> EXTEND price * 2 AS doubled |> SELECT doubled |> WHERE doubled > 10",
            "SELECT * FROM (FROM orders |> SELECT id) AS p",
            "FROM orders |> JOIN other ON orders.id = other.id |> SELECT orders.id |> DISTINCT",
        )) {
            val result = assertIs<TrinoPipesEngine.Transpile.Ok>(TrinoPipesEngine.transpile(sql))
            assertNull(result.executionError, "$sql: ${result.executionError}")
            assertFalse(TrinoPipes.containsPipeOperator(result.sql))
            assertNull(TrinoStatementValidator.parse(result.sql))
            assertNotNull(result.result.sourceMap)
        }
    }
    @Test fun lexicalOwnershipAndRoutineBoundaries() {
        for (sql in listOf("SELECT '|>'", "SELECT 1 -- |> hi", "SELECT \"|>\" FROM t", "SELECT 'broken |> hidden"))
            assertFalse(TrinoPipes.containsPipeOperator(sql), sql)
        val sql = "CREATE FUNCTION f() RETURNS bigint BEGIN RETURN 1; END; FROM t |> LIMIT 1; SELECT 2;"
        val chunks = TrinoPipes.chunks(sql)
        assertEquals(3, chunks.size)
        assertTrue(chunks[0].text.contains("RETURN 1; END;"))
        assertTrue(chunks[1].hasPipeOperator)
        assertNotNull(TrinoPipes.chunks("FROM t |> WHERE x = 'bad; SELECT 2").single().boundaryError)
    }
    @Test fun rejectedStatementsAndParametersNeverFallThrough() {
        val adapter = TrinoExecutionAdapter()
        assertFailsWith<SqlExecutionRefusal> { adapter.translate("FROM t |> BOGUS 2", false) }
        assertFailsWith<SqlExecutionRefusal> { adapter.translate("FROM t |> WHERE x = 'bad", false) }
        assertTrue(TrinoPipes.containsPipeOperator("SHOW TABLES |> WHERE x = 1"))
        assertFailsWith<SqlExecutionRefusal> { adapter.translate("SHOW TABLES |> WHERE x = 1", false) }
        val sql = "\n FROM t |> WHERE id = :id |> SELECT id"
        val param = adapter.parameters(sql).single()
        assertEquals(":id", sql.substring(param.start, param.end))
        adapter.translate(sql, true)
        assertFailsWith<SqlExecutionRefusal> { adapter.translate(sql, false) }
        adapter.translate(sql.replace(":id", "17"), false)
    }
    @Test fun initialStageIsScopedToAClaimedPipeline() {
        assertIs<TrinoPipesEngine.Transpile.NotPipe>(TrinoPipesEngine.transpile("FROM t"))
        val sql = "FROM t |> SELECT id |> LIMIT 2"
        val first = assertNotNull(TrinoPipesEngine.stagePrefixAt(sql, 2))
        assertEquals(1, first.stage)
        assertIs<TrinoPipesEngine.Transpile.Ok>(TrinoPipesEngine.transpile(first.text, allowFirstQueryStage = true))
        assertIs<TrinoPipesEngine.Transpile.NotPipe>(TrinoPipesEngine.transpile("SELECT 1"))
        val selectHead = assertNotNull(TrinoPipesEngine.stagePrefixAt("SELECT 1 AS id |> WHERE id > 0", 3))
        assertIs<TrinoPipesEngine.Transpile.Ok>(TrinoPipesEngine.transpile(selectHead.text, allowFirstQueryStage = true))
        val nested = "FROM (FROM t |> SELECT id |> WHERE id > 0) AS inner_query |> SELECT id"
        assertNull(TrinoPipesEngine.stagePrefixAt(nested, nested.indexOf("WHERE")))
        assertNotNull(TrinoPipesEngine.stagePrefixAt(nested, nested.lastIndexOf("SELECT")))
    }
}

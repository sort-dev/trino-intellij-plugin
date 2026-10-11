package dev.sort.trino.pipes

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.sql.DriverManager
import kotlin.test.*

class TrinoPipesLiveTest {
    @Test fun generatedPipelinesMatchNativeResults() {
        val url = System.getProperty("trino.live.url")
        assumeTrue("Live Trino URL not configured", !url.isNullOrBlank())
        DriverManager.getConnection(url).use { connection ->
            fun rows(sql: String): List<List<String?>> = connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result -> buildList {
                    while (result.next()) add((1..result.metaData.columnCount).map { result.getString(it) })
                } }
            }
            val cases = listOf(
                "FROM tpch.tiny.orders |> WHERE orderkey < 10 |> SELECT orderkey |> ORDER BY orderkey" to
                    "SELECT orderkey FROM tpch.tiny.orders WHERE orderkey < 10 ORDER BY orderkey",
                "FROM tpch.tiny.orders |> EXTEND orderkey * 2 AS doubled |> AGGREGATE sum(doubled) AS total GROUP BY orderstatus |> ORDER BY orderstatus" to
                    "SELECT orderstatus, sum(orderkey * 2) AS total FROM tpch.tiny.orders GROUP BY orderstatus ORDER BY orderstatus",
                "SELECT * FROM (FROM tpch.tiny.orders |> WHERE orderkey < 4 |> SELECT orderkey) AS p ORDER BY orderkey" to
                    "SELECT orderkey FROM tpch.tiny.orders WHERE orderkey < 4 ORDER BY orderkey",
                "FROM tpch.tiny.orders AS o |> LEFT JOIN tpch.tiny.customer AS c ON o.custkey=c.custkey |> WHERE o.orderkey < 4 |> SELECT o.orderkey, c.custkey |> ORDER BY orderkey" to
                    "SELECT o.orderkey, c.custkey FROM tpch.tiny.orders o LEFT JOIN tpch.tiny.customer c ON o.custkey=c.custkey WHERE o.orderkey < 4 ORDER BY o.orderkey",
            )
            for ((pipe, native) in cases) {
                val result = assertIs<TrinoPipesEngine.Transpile.Ok>(TrinoPipesEngine.transpile(pipe, beforeSubstitution = false))
                assertNull(result.executionError)
                assertEquals(rows(native), rows(result.sql), "$pipe\n${result.sql}")
            }
        }
    }
}

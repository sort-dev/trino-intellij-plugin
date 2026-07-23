package dev.sort.trino.probe

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

/**
 * DELIVERABLE C — the console behaviours a Trino (Brikk) query console leans on, measured against the
 * live server through io.trino:trino-jdbc:483. Cancel (client AND server-side — the doris "never trust
 * the client alone" lesson), grid-shaped statements, transactions, and SET SESSION round-trip.
 *
 * GATED: Assumes out unless `-Dtrino.live.url=...` is set.
 */
class TrinoConsoleTruthTest {

    private val liveUrl: String? = System.getProperty("trino.live.url")
    private val base: String get() = liveUrl!!.substringBefore("?")
    private val sessionArg: String get() = liveUrl!!.substringAfter("?", "sessionUser=truth")
    private var conn: Connection? = null

    @Before
    fun setUp() {
        assumeTrue("trino.live.url not set — live console suite skipped", !liveUrl.isNullOrBlank())
        conn = DriverManager.getConnection(liveUrl)
    }

    @After
    fun tearDown() {
        conn?.let { c -> runCatching { c.close() } }
    }

    private fun colCount(rs: ResultSet) = rs.metaData.columnCount
    private fun rowCount(rs: ResultSet): Int { var n = 0; while (rs.next()) n++; return n }
    private fun scalar(sql: String): String =
        conn!!.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getString(1) } }

    @Test
    fun `Statement cancel aborts the query client-side AND server-side, connection stays usable`() {
        // Unique marker so we can find EXACTLY this execution in system.runtime.queries. A modulo
        // predicate over a sf10 self cross join forces real per-row work (a bare count(*) of a cross
        // join can be answered from stats) — minutes of compute, cannot finish before we cancel.
        val marker = "trino_truth_cxl_" + java.lang.Long.toHexString(System.nanoTime())
        val heavy = "SELECT count(*) /* $marker */ FROM tpch.sf10.lineitem l1, tpch.sf10.lineitem l2 " +
            "WHERE (l1.orderkey + l2.orderkey) % 7 = 0"

        conn!!.createStatement().use { stmt ->
            val error = arrayOfNulls<Throwable>(1)
            val finishedNormally = booleanArrayOf(false)
            val start = System.currentTimeMillis()
            val worker = Thread {
                try {
                    stmt.executeQuery(heavy).use { rs -> rs.next(); finishedNormally[0] = true }
                } catch (t: Throwable) {
                    error[0] = t
                }
            }
            worker.isDaemon = true
            worker.start()
            Thread.sleep(1500)
            stmt.cancel()
            worker.join(30_000)
            val elapsed = System.currentTimeMillis() - start
            println("=== cancel (client) === finishedNormally=${finishedNormally[0]} elapsedMs=$elapsed thrown=${error[0]}")

            assertFalse("worker still running 30s after cancel", worker.isAlive)
            assertFalse("the long query completed instead of being cancelled", finishedNormally[0])
            val thrown = error[0]
            assertNotNull("cancel surfaced no error", thrown)
            assertTrue("cancel error was not a SQLException: ${thrown!!.javaClass.name}", thrown is SQLException)
            assertTrue("cancel took implausibly long (${elapsed}ms) — not a prompt abort", elapsed < 25_000)
        }

        // The connection is not poisoned: a fresh statement runs normally.
        assertEquals("connection unusable after cancel", "42", scalar("SELECT 42"))

        // SERVER-SIDE TRUTH (the doris lesson): a client exception is NOT proof the engine stopped.
        // Poll system.runtime.queries for THIS query by its marker — it must reach FAILED, not linger
        // RUNNING. trino-jdbc cancels via a DELETE to the query REST endpoint, so this is expected to
        // hold — but we verify it rather than trust the client.
        DriverManager.getConnection("$base?$sessionArg").use { mon ->
            var state: String? = null
            for (i in 0 until 15) {
                Thread.sleep(700)
                mon.createStatement().use { s ->
                    s.executeQuery(
                        "SELECT state FROM system.runtime.queries " +
                            "WHERE query LIKE '%$marker%' AND query NOT LIKE '%runtime.queries%' LIMIT 1",
                    ).use { rs -> if (rs.next()) state = rs.getString(1) }
                }
                if (state != null && state != "RUNNING" && state != "QUEUED") break
            }
            println("=== cancel (server-side) === marker=$marker finalState=$state")
            assertNotNull("cancelled query never appeared in system.runtime.queries", state)
            assertEquals("server-side query did not reach FAILED after client cancel — it kept running", "FAILED", state)
        }
    }

    @Test
    fun `grid-shaped statements each return a renderable ResultSet`() {
        val cases = linkedMapOf(
            "SHOW CATALOGS" to "SHOW CATALOGS",
            "SHOW SCHEMAS" to "SHOW SCHEMAS FROM tpch",
            "DESCRIBE" to "DESCRIBE tpch.tiny.orders",
            "EXPLAIN" to "EXPLAIN SELECT * FROM tpch.tiny.orders",
        )
        for ((label, sql) in cases) {
            conn!!.createStatement().use { s ->
                s.executeQuery(sql).use { rs ->
                    val cols = colCount(rs); val rows = rowCount(rs)
                    println("=== $label === cols=$cols rows=$rows")
                    assertTrue("$label produced no columns — grid empty", cols >= 1)
                    assertTrue("$label produced no rows — grid empty", rows >= 1)
                }
            }
        }
    }

    @Test
    fun `EXPLAIN TYPE VALIDATE is the live-validation seam — passes valid, rejects unresolved names`() {
        // Valid query -> single boolean 'true'. This is the future in-IDE live-validation hook.
        conn!!.createStatement().use { s ->
            s.executeQuery("EXPLAIN (TYPE VALIDATE) SELECT * FROM tpch.tiny.orders").use { rs ->
                assertTrue("EXPLAIN (TYPE VALIDATE) returned no row", rs.next())
                println("=== EXPLAIN (TYPE VALIDATE) valid === ${rs.getString(1)}")
                assertEquals("true", rs.getString(1))
            }
        }
        // Unresolved column -> semantic error (name resolution, not just syntax). Pin the message shape.
        val err = try {
            conn!!.createStatement().use { s -> s.executeQuery("EXPLAIN (TYPE VALIDATE) SELECT no_such_col FROM tpch.tiny.orders").use { } }
            null
        } catch (e: SQLException) {
            e.message
        }
        println("=== EXPLAIN (TYPE VALIDATE) invalid === $err")
        assertNotNull("EXPLAIN (TYPE VALIDATE) did not reject an unresolved column", err)
        assertTrue("expected a column-resolution error, got '$err'", err!!.contains("cannot be resolved"))
    }

    @Test
    fun `autocommit defaults true, transactions parse, memory rejects non-autocommit writes`() {
        val c = conn!!
        assertTrue("expected autoCommit=true by default", c.autoCommit)

        // Trino supports SQL transaction control; a read-only transaction commits cleanly.
        c.createStatement().use { s ->
            s.execute("START TRANSACTION")
            s.executeQuery("SELECT 1").use { it.next() }
            s.execute("COMMIT")
        }

        // But the memory connector only accepts writes in autocommit — pin the exact error a console
        // would surface if a user wraps a memory INSERT in a transaction.
        val tbl = "ttx_" + java.lang.Long.toHexString(System.nanoTime())
        c.createStatement().use { it.execute("CREATE TABLE memory.default.$tbl (id bigint)") }
        try {
            val err = try {
                c.createStatement().use { s ->
                    s.execute("START TRANSACTION")
                    s.execute("INSERT INTO memory.default.$tbl VALUES (1)")
                    s.execute("COMMIT")
                }
                null
            } catch (e: SQLException) {
                runCatching { c.createStatement().use { it.execute("ROLLBACK") } }
                e.message
            }
            println("=== memory write in transaction === $err")
            assertNotNull("memory INSERT inside a transaction unexpectedly succeeded", err)
            assertTrue("expected the autocommit-only error, got '$err'",
                err!!.contains("Catalog only supports writes using autocommit: memory"))
        } finally {
            runCatching { c.createStatement().use { it.execute("DROP TABLE IF EXISTS memory.default.$tbl") } }
        }
    }

    @Test
    fun `SET SESSION round-trips through SHOW SESSION on the same connection`() {
        val c = conn!!
        c.createStatement().use { it.execute("SET SESSION query_max_run_time = '2h'") }
        // trino-jdbc applies SET SESSION client-side to subsequent statements on the same connection.
        val rows = c.createStatement().use { s ->
            s.executeQuery("SHOW SESSION LIKE 'query_max_run_time'").use { rs ->
                val md = rs.metaData
                val out = ArrayList<Map<String, String?>>()
                while (rs.next()) out.add((1..md.columnCount).associate { md.getColumnLabel(it) to rs.getString(it) })
                out
            }
        }
        println("=== SHOW SESSION query_max_run_time === $rows")
        val row = rows.firstOrNull { it["Name"] == "query_max_run_time" }
        assertNotNull("query_max_run_time not present in SHOW SESSION", row)
        assertEquals("SET SESSION value did not round-trip", "2h", row!!["Value"])
    }
}

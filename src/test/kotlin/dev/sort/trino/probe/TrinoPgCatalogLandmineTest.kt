package dev.sort.trino.probe

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * DELIVERABLE D — the LIVE half of the introspector landmine (the seed's #1 finding).
 *
 * [TrinoIntrospectorGateTest] proved statically that the platform selects the NATIVE (PG)
 * introspector for TRINO_BRIKK whenever the server reports major version >= 9. This test pins the two
 * live facts that make that a LANDMINE rather than a curiosity:
 *
 *   1. Trino reports `getDatabaseMajorVersion() == 483` (>= 9) — the gate PASSES (unlike DuckDB's 1).
 *   2. Trino has NO `pg_catalog` and none of the PG catalog functions — so the PG introspector's L1
 *      queries (pg_class / pg_namespace / pg_attribute joins, `format_type`, `current_setting`) would
 *      ALL throw at attach time. The introspection the platform would pick cannot run at all.
 *
 * `information_schema` (the ANSI surface the generic JDBC introspector effectively rides) works
 * per-catalog — that is the safe engine, contrasted here.
 *
 * GATED: Assumes out unless `-Dtrino.live.url=...` is set.
 */
class TrinoPgCatalogLandmineTest {

    private val liveUrl: String? = System.getProperty("trino.live.url")
    private var conn: Connection? = null

    @Before
    fun setUp() {
        assumeTrue("trino.live.url not set — live introspector-landmine suite skipped", !liveUrl.isNullOrBlank())
        conn = DriverManager.getConnection(liveUrl)
    }

    @After
    fun tearDown() {
        conn?.let { c -> runCatching { c.close() } }
    }

    /** @return true if the SQL executes without error (result set discarded). */
    private fun runs(sql: String): Boolean =
        try {
            conn!!.createStatement().use { s -> s.executeQuery(sql).use { true } }
        } catch (e: Exception) {
            false
        }

    @Test
    fun `Trino reports a major version that CLEARS the PG native-introspector gate`() {
        val md = conn!!.metaData
        val major = md.databaseMajorVersion
        println("=== version === product=${md.databaseProductName} version=${md.databaseProductVersion} major=$major driver=${md.driverName} ${md.driverVersion}")
        // The exact inverse of the DuckDB finding: there, major==1 < 9 kept the native introspector OUT.
        // Here 483 >= 9 lets it IN — which is only safe if Trino can answer pg_catalog. It cannot (below).
        assertTrue("Trino major version must be >= 9 (PG native-introspector gate) — got $major", major >= 9)
    }

    @Test
    fun `pg_catalog and the PG introspector functions are ABSENT`() {
        val report = StringBuilder("\n=== pg_catalog emulation on Trino 483 ===\n")
        // The backbone objects/functions a PG-style L1 introspection query reads first.
        val probes = linkedMapOf(
            "pg_catalog.pg_namespace" to "SELECT * FROM pg_catalog.pg_namespace LIMIT 1",
            "pg_catalog.pg_class" to "SELECT * FROM pg_catalog.pg_class LIMIT 1",
            "pg_catalog.pg_attribute" to "SELECT * FROM pg_catalog.pg_attribute LIMIT 1",
            "pg_catalog.pg_type" to "SELECT * FROM pg_catalog.pg_type LIMIT 1",
            "format_type()" to "SELECT format_type(NULL, NULL)",
            "current_setting('server_version')" to "SELECT current_setting('server_version')",
            "pg_table_is_visible()" to "SELECT pg_table_is_visible(0)",
        )
        val present = ArrayList<String>()
        for ((name, sql) in probes) {
            val ok = runs(sql)
            report.append("  ${if (ok) "PRESENT" else "ABSENT "} $name\n")
            if (ok) present.add(name)
        }
        println(report)
        // If ANY of these ever start resolving, the PG introspector might partially run — re-open the
        // decision in REPORT-truth-tree.md.
        assertTrue(
            "pg_catalog surface unexpectedly present on Trino: $present — the PG introspector might " +
                "partially run; revisit the introspector decision",
            present.isEmpty(),
        )
    }

    @Test
    fun `a PG-style L1 introspection join FAILS on Trino`() {
        // The exact shape of the PG introspector's table-list query — proves it throws, so leaving the
        // native introspector selected would surface as introspection errors at attach.
        val pgL1 = "SELECT n.nspname, c.relname, c.relkind FROM pg_catalog.pg_class c " +
            "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE c.relkind = 'r'"
        println("=== PG L1 join runnable on Trino? === ${runs(pgL1)}")
        assertFalse("a pg_catalog L1 join must NOT run on Trino (no pg_catalog)", runs(pgL1))
    }

    @Test
    fun `information_schema — the safe generic-introspector surface — DOES work per catalog`() {
        // The contrast: the ANSI surface the generic JDBC introspector effectively reads is present.
        assertTrue("tpch.information_schema.tables must be queryable", runs("SELECT count(*) FROM tpch.information_schema.tables"))
        assertTrue("tpch.information_schema.columns must be queryable", runs("SELECT count(*) FROM tpch.information_schema.columns"))
    }
}

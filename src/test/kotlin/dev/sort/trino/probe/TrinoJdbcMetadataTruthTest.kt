package dev.sort.trino.probe

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * DELIVERABLE B — what the official io.trino:trino-jdbc:483 driver reports through
 * [DatabaseMetaData]. This is the surface the platform's generic JDBC introspector reads for a
 * TRINO_BRIKK data source (see [TrinoIntrospectorGateTest] + [TrinoPgCatalogLandmineTest] for why the
 * generic introspector is the ONLY safe engine here). Facts, not aspirations: where the driver returns
 * empty by design, the behavior is pinned with a `// GAP:` note and recorded in REPORT-truth-tree.md.
 *
 * Multi-catalog is exercised for real (Trino's connectors ARE catalogs): tpch.tiny + a table created
 * over the wire in the `memory` connector, dropped in tearDown.
 *
 * GATED: Assumes out unless `-Dtrino.live.url=...` is set.
 */
class TrinoJdbcMetadataTruthTest {

    private val liveUrl: String? = System.getProperty("trino.live.url")
    private val base: String get() = liveUrl!!.substringBefore("?")
    private val sessionArg: String get() = liveUrl!!.substringAfter("?", "sessionUser=truth")

    private val sfx = "_" + java.lang.Long.toHexString(System.nanoTime())
    private val table = "ttm$sfx"
    private var conn: Connection? = null

    @Before
    fun setUp() {
        assumeTrue("trino.live.url not set — live metadata suite skipped", !liveUrl.isNullOrBlank())
        val c = DriverManager.getConnection("$base?$sessionArg")
        conn = c
        c.createStatement().use { s ->
            s.execute("DROP TABLE IF EXISTS memory.default.$table")
            // Task-specified shape (array/map/row) + a NOT NULL column to pin nullability reporting.
            s.execute(
                "CREATE TABLE memory.default.$table (" +
                    "id bigint, name varchar, tags array(varchar), " +
                    "meta map(varchar,varchar), s row(a bigint, b varchar), nn bigint NOT NULL)",
            )
        }
    }

    @After
    fun tearDown() {
        conn?.let { c ->
            runCatching { c.createStatement().use { it.execute("DROP TABLE IF EXISTS memory.default.$table") } }
            runCatching { c.close() }
        }
    }

    private fun rows(rs: ResultSet): List<Map<String, String?>> {
        val md = rs.metaData
        val out = ArrayList<Map<String, String?>>()
        rs.use { while (rs.next()) out.add((1..md.columnCount).associate { md.getColumnLabel(it) to rs.getString(it) }) }
        return out
    }

    @Test
    fun `getCatalogs lists the connectors as catalogs`() {
        val cats = rows(conn!!.metaData.catalogs).mapNotNull { it["TABLE_CAT"] }
        println("=== getCatalogs === $cats")
        // HEADLINE: every Trino connector is a catalog — the basis for the multi-catalog tree.
        assertTrue("expected tpch/memory/system among catalogs, got $cats", cats.containsAll(listOf("tpch", "memory", "system")))
    }

    @Test
    fun `getSchemas and the per-catalog filter`() {
        val md = conn!!.metaData
        val all = rows(md.schemas)
        assertTrue("getSchemas() returned nothing", all.isNotEmpty())
        assertTrue("getSchemas missing TABLE_CATALOG column", all.first().containsKey("TABLE_CATALOG"))

        val tpch = rows(md.getSchemas("tpch", null))
        val schemaNames = tpch.map { it["TABLE_SCHEM"] }
        println("=== getSchemas(catalog=tpch) === $schemaNames")
        assertTrue("tpch schemas must include 'tiny' and 'information_schema', got $schemaNames",
            schemaNames.containsAll(listOf("tiny", "information_schema")))
        assertTrue("catalog filter leaked non-tpch catalogs: ${tpch.map { it["TABLE_CATALOG"] }.toSet()}",
            tpch.all { it["TABLE_CATALOG"] == "tpch" })
    }

    @Test
    fun `getTables sees tpch_tiny and the over-the-wire memory table`() {
        val md = conn!!.metaData
        val orders = rows(md.getTables("tpch", "tiny", "orders", arrayOf("TABLE")))
        println("=== getTables(tpch.tiny.orders) === ${orders.map { "${it["TABLE_CAT"]}.${it["TABLE_SCHEM"]}.${it["TABLE_NAME"]}(${it["TABLE_TYPE"]})" }}")
        assertTrue("tpch.tiny.orders not visible as a TABLE", orders.any { it["TABLE_NAME"] == "orders" && it["TABLE_TYPE"] == "TABLE" })

        val mine = rows(md.getTables("memory", "default", table, null))
        println("=== getTables(memory.default.$table) === ${mine.map { it["TABLE_CAT"] + "." + it["TABLE_NAME"] }}")
        assertTrue("the memory table just created is not visible", mine.any { it["TABLE_NAME"] == table })
        assertTrue("catalog filter leaked non-memory catalogs: ${mine.map { it["TABLE_CAT"] }.toSet()}",
            mine.all { it["TABLE_CAT"] == "memory" })
    }

    @Test
    fun `getColumns preserves array map row spellings and reports NOT NULL`() {
        val cols = rows(conn!!.metaData.getColumns("memory", "default", table, null)).associateBy { it["COLUMN_NAME"] }
        cols.values.forEach { println("  col ${it["COLUMN_NAME"]} | TYPE_NAME=${it["TYPE_NAME"]} | DATA_TYPE=${it["DATA_TYPE"]} | NULLABLE=${it["NULLABLE"]} | IS_NULLABLE=${it["IS_NULLABLE"]}") }

        // Full Trino type spellings survive into TYPE_NAME — usable directly for tree/grid tooltips.
        assertEquals("bigint", cols["id"]!!["TYPE_NAME"])
        assertEquals("varchar", cols["name"]!!["TYPE_NAME"])
        assertEquals("array(varchar)", cols["tags"]!!["TYPE_NAME"])
        assertEquals("map(varchar, varchar)", cols["meta"]!!["TYPE_NAME"])
        assertTrue("row spelling not preserved: ${cols["s"]!!["TYPE_NAME"]}", cols["s"]!!["TYPE_NAME"]!!.startsWith("row("))

        // Nullability: Trino columns are nullable by DEFAULT; the memory connector DOES honour and
        // report NOT NULL (NULLABLE=columnNoNulls / IS_NULLABLE=NO). Pinned as true.
        assertEquals("default columns must be nullable", DatabaseMetaData.columnNullable, cols["id"]!!["NULLABLE"]!!.toInt())
        assertEquals("NOT NULL column must report columnNoNulls", DatabaseMetaData.columnNoNulls, cols["nn"]!!["NULLABLE"]!!.toInt())
        assertEquals("NO", cols["nn"]!!["IS_NULLABLE"])
    }

    @Test
    fun `getPrimaryKeys and getImportedKeys are EMPTY — Trino has no PK or FK`() {
        val md = conn!!.metaData
        val pk = rows(md.getPrimaryKeys("tpch", "tiny", "orders"))
        val fk = rows(md.getImportedKeys("tpch", "tiny", "orders"))
        println("=== getPrimaryKeys=${pk.size} getImportedKeys=${fk.size} (tpch.tiny.orders) ===")
        // GAP (by engine design, not a driver bug): Trino models no primary/foreign keys, so the tree
        // shows none. Nothing to enrich here — it is a property of the engine.
        assertTrue("Trino unexpectedly reported a primary key — revisit REPORT-truth-tree.md", pk.isEmpty())
        assertTrue("Trino unexpectedly reported a foreign key — revisit REPORT-truth-tree.md", fk.isEmpty())
    }

    @Test
    fun `getTableTypes and getTypeInfo are populated`() {
        val types = rows(conn!!.metaData.tableTypes).mapNotNull { it["TABLE_TYPE"] }
        println("=== getTableTypes === $types")
        assertTrue("TABLE type missing", types.contains("TABLE"))
        assertFalse("getTypeInfo() returned no rows — grid type mapping would fall back", rows(conn!!.metaData.typeInfo).isEmpty())
    }

    @Test
    fun `connection getCatalog is null without a URL catalog, and the URL catalog when set`() {
        // The setUp connection has no catalog in its URL.
        println("=== getCatalog (no URL catalog) === ${conn!!.catalog}")
        assertEquals("getCatalog() must be null when the URL names no catalog", null, conn!!.catalog)

        DriverManager.getConnection("$base/memory/default?$sessionArg").use { c ->
            println("=== getCatalog (URL /memory/default) === ${c.catalog}")
            // TRUTH: getCatalog() reflects the URL catalog, not a live 'current catalog'. The tree must
            // enumerate via getCatalogs(), never assume getCatalog() is populated.
            assertEquals("memory", c.catalog)
        }
    }
}

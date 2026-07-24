package dev.sort.trino.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * OFFLINE unit tests for the completion-time targeting decision — the "enumerated-but-childless"
 * detection and level inference — exercised with a fake model tree, no live DataGrip needed. This
 * is the whole reason [TrinoNamespacePath] is abstracted off `DasObject`.
 */
class TrinoNamespacePathTest {

    /** Minimal [TrinoNamespacePath.Node] fake. */
    private class Fake(
        override val name: String,
        private val kids: List<Fake> = emptyList(),
    ) : TrinoNamespacePath.Node {
        override fun childNodes(): List<TrinoNamespacePath.Node> = kids
    }

    // A model where:
    //  - hive: enumerated catalog, schemas NOT loaded (childless)
    //  - tpch: catalog with schema `tiny` enumerated but tables NOT loaded (childless schema)
    //  - system: catalog with schema `runtime` that HAS a table (fully loaded)
    private val hive = Fake("hive")
    private val tiny = Fake("tiny")
    private val tpch = Fake("tpch", listOf(tiny))
    private val runtime = Fake("runtime", listOf(Fake("queries")))
    private val system = Fake("system", listOf(runtime))
    private val roots = listOf(hive, tpch, system)

    @Test
    fun resolvesCatalogRootByName() {
        assertSame(hive, TrinoNamespacePath.resolve(roots, listOf("hive")))
        assertSame(tiny, TrinoNamespacePath.resolve(roots, listOf("tpch", "tiny")))
    }

    @Test
    fun resolveIsCaseInsensitive() {
        assertSame(tiny, TrinoNamespacePath.resolve(roots, listOf("TPCH", "Tiny")))
    }

    @Test
    fun unresolvedSegmentResolvesToNull() {
        assertNull("a non-existent catalog must not resolve", TrinoNamespacePath.resolve(roots, listOf("nope")))
        assertNull("a non-existent schema must not resolve", TrinoNamespacePath.resolve(roots, listOf("tpch", "nope")))
    }

    @Test
    fun childlessCatalogDecidesCatalogDeepen() {
        val d = TrinoNamespacePath.decideDeepen(roots, listOf("hive"))
        assertEquals(TrinoNamespacePath.Level.CATALOG, d!!.level)
        assertEquals("hive", d.catalog)
        assertNull(d.schema)
        assertSame(hive, d.node)
    }

    @Test
    fun childlessSchemaDecidesSchemaDeepen() {
        val d = TrinoNamespacePath.decideDeepen(roots, listOf("tpch", "tiny"))
        assertEquals(TrinoNamespacePath.Level.SCHEMA, d!!.level)
        assertEquals("tpch", d.catalog)
        assertEquals("tiny", d.schema)
        assertSame(tiny, d.node)
    }

    @Test
    fun catalogWithLoadedSchemasDoesNotDeepen() {
        // tpch already has a child schema -> not childless -> normal completion handles it.
        assertNull(TrinoNamespacePath.decideDeepen(roots, listOf("tpch")))
    }

    @Test
    fun schemaWithLoadedTablesDoesNotDeepen() {
        assertNull(TrinoNamespacePath.decideDeepen(roots, listOf("system", "runtime")))
    }

    @Test
    fun unresolvedPathDoesNotDeepen() {
        // Mid-typing / typo: nothing resolves, so nothing is kicked (never spam introspection).
        assertNull(TrinoNamespacePath.decideDeepen(roots, listOf("hiv")))
        assertNull(TrinoNamespacePath.decideDeepen(roots, listOf("tpch", "tin")))
    }

    @Test
    fun pathDeeperThanSchemaIsIgnored() {
        // catalog.schema.table.<caret> addresses a table; columns come with the table, so the
        // platform owns it — we only deepen catalog (schemas) and schema (tables).
        assertNull(TrinoNamespacePath.decideDeepen(roots, listOf("system", "runtime", "queries")))
        assertNull(TrinoNamespacePath.decideDeepen(roots, emptyList()))
    }
}

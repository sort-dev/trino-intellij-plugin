package dev.sort.trino.catalog

import com.intellij.database.model.DasObject
import dev.brikk.house.sql.intellij.catalog.NamespacePathResolver
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** All nine pre-unification namespace decisions, against the shared resolver. */
class TrinoNamespacePathTest {
    private class Fake(val name: String, val children: List<Fake> = emptyList()) {
        val node: DasObject = Proxy.newProxyInstance(DasObject::class.java.classLoader, arrayOf(DasObject::class.java)) { proxy, method, args ->
            when (method.name) {
                "getName" -> name
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> name
                else -> error("Unexpected fake model call: ${method.name}")
            }
        } as DasObject
    }
    private val hive = Fake("hive")
    private val tiny = Fake("tiny")
    private val tpch = Fake("tpch", listOf(tiny))
    private val runtime = Fake("runtime", listOf(Fake("queries")))
    private val system = Fake("system", listOf(runtime))
    private val all = listOf(hive, tiny, tpch, runtime, system) + runtime.children
    private val roots = listOf(hive, tpch, system).map { it.node }
    private val resolver = NamespacePathResolver(TrinoCatalogProfile.levels, false, childrenOf = { node ->
        all.single { it.node === node }.children.map { it.node }
    })
    private fun resolve(vararg path: String) = resolver.resolve(roots, path.toList())
    private fun deepen(vararg path: String) = resolve(*path)?.takeIf { resolver.isChildless(it.node) }

    @Test fun resolvesCatalogRootByName() {
        assertSame(hive.node, resolve("hive")?.node)
        assertSame(tiny.node, resolve("tpch", "tiny")?.node)
    }
    @Test fun resolveIsCaseInsensitive() { assertSame(tiny.node, resolve("TPCH", "Tiny")?.node) }
    @Test fun unresolvedSegmentResolvesToNull() {
        assertNull(resolve("nope")); assertNull(resolve("tpch", "nope"))
    }
    @Test fun childlessCatalogDecidesCatalogDeepen() {
        val target = requireNotNull(deepen("hive"))
        assertEquals(listOf("hive"), target.path); assertSame(hive.node, target.node)
    }
    @Test fun childlessSchemaDecidesSchemaDeepen() {
        val target = requireNotNull(deepen("tpch", "tiny"))
        assertEquals(listOf("tpch", "tiny"), target.path); assertSame(tiny.node, target.node)
    }
    @Test fun catalogWithLoadedSchemasDoesNotDeepen() { assertNull(deepen("tpch")) }
    @Test fun schemaWithLoadedTablesDoesNotDeepen() { assertNull(deepen("system", "runtime")) }
    @Test fun unresolvedPathDoesNotDeepen() { assertNull(deepen("hiv")); assertNull(deepen("tpch", "tin")) }
    @Test fun pathDeeperThanSchemaIsIgnored() {
        assertNull(deepen("system", "runtime", "queries")); assertNull(deepen())
    }
}

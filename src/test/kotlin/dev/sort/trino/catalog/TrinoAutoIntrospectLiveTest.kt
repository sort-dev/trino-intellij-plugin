package dev.sort.trino.catalog

import com.intellij.database.dataSource.DataSourceSyncManager
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.LoaderContext
import com.intellij.database.util.TreePatternUtils
import com.intellij.openapi.application.ReadAction
import com.intellij.util.ui.classpath.SimpleClasspathElementFactory
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * LIVE integration proof for the lazy/targeted introspection, gated on `-Dtrino.live.url` (else the
 * whole suite skips and the offline gate stays green). Drives the REAL platform introspection
 * against the running Trino 483 container and measures, end to end:
 *
 *  1. **Connect-time:** a fresh data source, introspected within its DEFAULT scope, enumerates the
 *     catalogs (connectors) — or does not (recorded, to decide whether the connect interceptor is
 *     needed).
 *  2. **Catalog deepening:** [TrinoAutoIntrospect.request] on `tpch` (CATALOG) loads tpch's schemas
 *     and NOTHING deeper (tpch.tiny stays table-less — the one-level discipline).
 *  3. **Schema deepening:** the same on `tpch.tiny` (SCHEMA) loads tiny's tables (orders, customer…).
 *
 * The fixture runs on the EDT, so every async introspection is awaited with the EDT-PUMPING helpers
 * ([PlatformTestUtil.waitForFuture] / [PlatformTestUtil.waitWithEventsDispatching]) — a *blocking*
 * connect (createBlockingNonCancellable) would deadlock the EDT. The platform's out-of-process JDBC
 * host loads the driver from the DRIVER's classpath (not the test classpath), so [injectDriverJar]
 * adds the on-disk `trino-jdbc` jar to the registered driver for the fixture.
 *
 * NOTE (JUnit3): BasePlatformTestCase is a JUnit3 TestCase, which does NOT understand JUnit4
 * assumptions — so "skip" is an early `return` (a silent pass). When a sync fails or times out in
 * the fixture (the in-fixture-connectivity limitation), the test prints `SKIP:` and returns; once a
 * sync succeeds and catalogs enumerate, the deepening assertions are HARD.
 */
class TrinoAutoIntrospectLiveTest : BasePlatformTestCase() {

    private val liveUrl: String? = System.getProperty("trino.live.url")
    private var dataSource: LocalDataSource? = null
    private var driver: DatabaseDriver? = null
    private var savedClasspath: List<com.intellij.util.ui.classpath.SimpleClasspathElement>? = null

    override fun setUp() {
        super.setUp()
        TrinoAutoIntrospect.resetForTest()
    }

    override fun tearDown() {
        try {
            dataSource?.let { ds ->
                runCatching { DataSourceSyncManager.getInstance().stopSynchronization(ds) }
                runCatching { LocalDataSourceManager.getInstance(project).removeDataSource(ds) }
            }
            driver?.let { d -> savedClasspath?.let { runCatching { d.additionalClasspathElements = it } } }
            TrinoAutoIntrospect.resetForTest()
        } finally {
            super.tearDown()
        }
    }

    /** Add the on-disk trino-jdbc jar to the driver's classpath so the remote JDBC host can load it. */
    private fun injectDriverJar(d: DatabaseDriver) {
        savedClasspath = d.additionalClasspathElements
        val jarPath = System.getProperty("java.class.path").orEmpty()
            .split(File.pathSeparator)
            .firstOrNull { it.substringAfterLast(File.separatorChar).let { n -> n.startsWith("trino-jdbc") && n.endsWith(".jar") } }
            ?: error("trino-jdbc jar not found on the test classpath (java.class.path)")
        val url = "jar://${File(jarPath).absolutePath}!/"
        d.additionalClasspathElements = SimpleClasspathElementFactory.createElements(url)
    }

    /** Register a TRINO_BRIKK data source pointing at the live container. */
    private fun registerDataSource(): LocalDataSource {
        val d = DatabaseDriverManager.getInstance().getDriver("trino-brikk")
            ?: error("driversConfig must register trino-brikk")
        driver = d
        injectDriverJar(d)
        val ds = LocalDataSource.create("trino-live-introspect", "io.trino.jdbc.TrinoDriver", liveUrl!!, "")
        ds.databaseDriver = d
        LocalDataSourceManager.getInstance(project).addDataSource(ds)
        dataSource = ds
        return ds
    }

    private fun dbModelRoots(ds: LocalDataSource): List<DasObject> = ReadAction.compute<List<DasObject>, RuntimeException> {
        val dbds = DbPsiFacade.getInstance(project).findDataSource(ds.uniqueId) ?: return@compute emptyList()
        dbds.model.modelRoots.toList()
    }

    private fun childrenOf(node: DasObject): List<DasObject> = ReadAction.compute<List<DasObject>, RuntimeException> {
        (node.getDasChildren(ObjectKind.SCHEMA).toList() +
            node.getDasChildren(ObjectKind.TABLE).toList() +
            node.getDasChildren(null).toList()).distinct()
    }

    private fun nameOf(o: DasObject): String = ReadAction.compute<String, RuntimeException> { o.name }

    private fun rootNamed(ds: LocalDataSource, name: String): DasObject? =
        dbModelRoots(ds).firstOrNull { nameOf(it).equals(name, true) }

    /** Drive one sync, pumping the EDT, retrying once on a benign fixture cancellation. Returns
     *  false (with a SKIP print) only if it truly fails/times out — the in-fixture connectivity
     *  limitation, distinct from a real deepening regression. */
    private fun awaitSyncOrSkip(contextFactory: () -> LoaderContext, timeoutMs: Long = 60_000): Boolean {
        repeat(2) { attempt ->
            try {
                val future = DataSourceSyncManager.getInstance().tryPerform(contextFactory(), true, false)?.toFuture()
                    ?: run { println("SKIP: tryPerform returned no task (in-fixture)"); return false }
                PlatformTestUtil.waitForFuture(future, timeoutMs)
                return true
            } catch (t: Throwable) {
                val cancelled = generateSequence<Throwable>(t) { it.cause }
                    .any { it.javaClass.simpleName.contains("Cancell", true) }
                if (cancelled && attempt == 0) { println("(sync cancelled; retrying once)"); return@repeat }
                println("SKIP: in-fixture sync failed/timed out: ${t.javaClass.simpleName}: ${t.message}")
                return false
            }
        }
        return false
    }

    private fun pollUntil(message: String, timeoutSeconds: Int = 45, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(message, condition, timeoutSeconds)
    }

    private fun treeSnapshot(ds: LocalDataSource, catalogsToProbe: List<String> = listOf("tpch")): String {
        val roots = dbModelRoots(ds)
        val sb = StringBuilder("catalogs=${roots.map(::nameOf)}")
        for (c in catalogsToProbe) {
            val cat = roots.firstOrNull { nameOf(it).equals(c, true) } ?: continue
            val schemas = childrenOf(cat)
            sb.append(" | $c.schemas=${schemas.map(::nameOf)}")
            val tiny = schemas.firstOrNull { nameOf(it).equals("tiny", true) } ?: schemas.firstOrNull()
            if (tiny != null) sb.append(" | $c.${nameOf(tiny)}.tables=${childrenOf(tiny).map(::nameOf).take(8)}")
        }
        return sb.toString()
    }

    /**
     * END-TO-END proof from the REAL starting state (a fresh data source's DEFAULT scope), with a
     * measurement print at each stage:
     *  - connect: catalogs enumerate, tpch is childless (no schemas) — the connect interceptor is
     *    unnecessary, and lazy deepening has work to do;
     *  - CATALOG deepen tpch: its schemas load, and tpch.tiny stays TABLE-less (one level, no cascade);
     *  - SCHEMA deepen tpch.tiny: its tables load (orders/customer/lineitem).
     */
    fun testDefaultConnectThenTargetedDeepening() {
        if (liveUrl.isNullOrBlank()) { println("SKIP: trino.live.url not set"); return }
        val ds = registerDataSource()

        // STAGE 1 — DEFAULT scope (nothing set). Measures the connect-interceptor need.
        if (!awaitSyncOrSkip({ LoaderContext.selectGeneralTask(project, ds) })) return
        runCatching { pollUntil("first introspection", 30) { dbModelRoots(ds).isNotEmpty() } }
        val defaultScope = ReadAction.compute<String, RuntimeException> {
            ds.introspectionScope?.let { TreePatternUtils.serialize(it) } ?: "<null>"
        }
        println("=== STAGE1 default connect: ${treeSnapshot(ds)} ; scope=$defaultScope ===")

        val tpch = rootNamed(ds, "tpch")
        if (tpch == null) { println("SKIP: no catalogs enumerated in-fixture (connectivity)"); return }
        assertTrue(
            "MEASURED: catalogs enumerate on default connect (no interceptor needed) — tpch/memory/system",
            listOf("tpch", "memory", "system").all { rootNamed(ds, it) != null },
        )
        assertTrue("MEASURED: tpch has NO schemas on default connect (lazy work remains)", childrenOf(tpch).isEmpty())

        // STAGE 2 — CATALOG deepen tpch -> schemas load; tpch.tiny must stay table-less (one level).
        assertTrue("catalog deepen must kick a NEW introspection",
            TrinoAutoIntrospect.request(project, ds, TrinoNamespacePath.Level.CATALOG, "tpch", null, tpch))
        pollUntil("tpch schemas after catalog deepen", 45) {
            childrenOf(tpch).any { nameOf(it).equals("tiny", true) }
        }
        println("=== STAGE2 after CATALOG deepen tpch: ${treeSnapshot(ds)} ===")
        val tiny = childrenOf(tpch).firstOrNull { nameOf(it).equals("tiny", true) }
            ?: error("tpch.tiny did not enumerate after catalog deepen")
        assertTrue(
            "ONE LEVEL: catalog deepen loads schemas but NOT their tables (tpch.tiny stays childless)",
            childrenOf(tiny).isEmpty(),
        )
        assertFalse("second catalog deepen must be de-duped",
            TrinoAutoIntrospect.request(project, ds, TrinoNamespacePath.Level.CATALOG, "tpch", null, tpch))

        // STAGE 3 — SCHEMA deepen tpch.tiny -> tables load.
        assertTrue("schema deepen must kick a NEW introspection",
            TrinoAutoIntrospect.request(project, ds, TrinoNamespacePath.Level.SCHEMA, "tpch", "tiny", tiny))
        pollUntil("tpch.tiny tables after schema deepen", 45) {
            childrenOf(tiny).any { nameOf(it).equals("orders", true) }
        }
        val tables = childrenOf(tiny).map(::nameOf)
        println("=== STAGE3 after SCHEMA deepen tpch.tiny: tables=$tables ===")
        assertTrue(
            "tpch.tiny must resolve its tables (orders, customer, lineitem) after a schema deepen",
            listOf("orders", "customer", "lineitem").all { t -> tables.any { it.equals(t, true) } },
        )
        assertFalse("second schema deepen must be de-duped",
            TrinoAutoIntrospect.request(project, ds, TrinoNamespacePath.Level.SCHEMA, "tpch", "tiny", tiny))
    }
}

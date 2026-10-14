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
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.registerOrReplaceServiceInstance
import dev.brikk.house.sql.intellij.catalog.NamespaceTarget
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import java.io.File
import com.intellij.database.console.JdbcConsole
import com.intellij.database.console.session.DatabaseSessionViewService
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.datagrid.DataAuditor
import com.intellij.database.datagrid.DataRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.intellij.catalog.CatalogSource
import dev.sort.trino.sql.TrinoSqlDialect
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * LIVE integration proof for the lazy/targeted introspection, gated on `-Dtrino.live.url` (else the
 * whole suite skips and the offline gate stays green). Drives the REAL platform introspection
 * against the running Trino 483 container and measures, end to end:
 *
 *  1. **Connect-time:** a fresh data source, introspected within its DEFAULT scope, enumerates the
 *     catalogs (connectors) — or does not (recorded, to decide whether the connect interceptor is
 *     needed).
 *  2. **Catalog deepening:** [TrinoAutoIntrospectService.request] on `tpch` (CATALOG) loads tpch's schemas
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
 * assumptions, so only an unconfigured lane returns early. Once enabled, connection, timeout and
 * model failures are hard failures, not silent skips.
 */
class TrinoAutoIntrospectLiveTest : BasePlatformTestCase() {

    private val liveUrl: String? = System.getProperty("trino.live.url")
    private var dataSource: LocalDataSource? = null
    private var driver: DatabaseDriver? = null
    private var savedClasspath: List<com.intellij.util.ui.classpath.SimpleClasspathElement>? = null
    private var savedArtifacts: List<DatabaseDriver.ArtifactRef>? = null
    private lateinit var errors: BackgroundErrors
    private lateinit var requests: TrinoAutoIntrospectService
    private var console: JdbcConsole? = null
    private var restoreStore: (() -> Unit)? = null
    private lateinit var liveScope: kotlinx.coroutines.CoroutineScope

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
        liveScope = errors.newScope(testRootDisposable)
        requests = TrinoAutoIntrospectService(project, liveScope)
        project.registerOrReplaceServiceInstance(TrinoAutoIntrospectService::class.java, requests, testRootDisposable)
        myFixture.configureByText("live-introspection.sql", "SELECT 1")
        if (!liveUrl.isNullOrBlank()) useStableDriverWorkingDirectory()
    }

    override fun tearDown() {
        RunAll(
            { liveScope.cancel() },
            { errors.awaitScopes() },
            { dataSource?.let { ds -> pollUntil("introspection stops before disposal", 10) { !DataSourceSyncManager.getInstance().isActive(ds) } } },
            { errors.assertNone() },
            { console?.let { DatabaseSessionViewService.getInstance().destroyView(it); Disposer.dispose(it) } },
            { dataSource?.let { DatabaseConnectionManager.getInstance().terminateConnections(it, null) } },
            { dataSource?.let { LocalDataSourceManager.getInstance(project).removeDataSource(it) } },
            { driver?.let { d -> savedClasspath?.let { d.additionalClasspathElements = it } } },
            { driver?.let { d -> savedArtifacts?.let { d.javaClass.getMethod("setArtifacts", List::class.java).invoke(d, it) } } },
            { restoreStore?.invoke() },
            { super.tearDown() },
        ).run()
    }

    private fun useStableDriverWorkingDirectory() {
        // A reused light project can point at an earlier fixture's deleted directory. Only override
        // the process working-directory lookup, not its model, connection or introspection code.
        val path = java.nio.file.Path.of("build", "introspection-parity", "live", "ide-work").toAbsolutePath()
        java.nio.file.Files.createDirectories(path)
        val store = project.javaClass.getMethod("getComponentStore").invoke(project)
        val original = store.javaClass.getMethod("getStoreDescriptor").invoke(store)
        val type = Class.forName("com.intellij.configurationStore.ProjectStoreDescriptor", true, store.javaClass.classLoader)
        val setter = store.javaClass.getMethod("setStoreDescriptor", type)
        val replacement = java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            if (method.name == "getHistoricalProjectBasePath") path else method.invoke(original, *(args ?: emptyArray()))
        }
        setter.invoke(store, replacement)
        restoreStore = { setter.invoke(store, original) }
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
        // 262+ validates every declared downloadable artifact, even with a complete additional
        // classpath. Use the already resolved fixture JAR rather than opening a download dialog.
        savedArtifacts = d.artifacts
        d.javaClass.getMethod("setArtifacts", List::class.java).invoke(d, emptyList<DatabaseDriver.ArtifactRef>())
    }

    /** Register a TRINO_BRIKK data source pointing at the live container. */
    private fun registerDataSource(): LocalDataSource {
        val d = DatabaseDriverManager.getInstance().getDriver("trino-brikk")
            ?: error("driversConfig must register trino-brikk")
        driver = d
        injectDriverJar(d)
        val ds = LocalDataSource.create("trino-live-introspect", "io.trino.jdbc.TrinoDriver", liveUrl!!, "")
        ds.databaseDriver = d
        ds.username = "introspection"
        ds.isAutoSynchronize = false
        ds.isKeepAlive = false
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

    /** Drive the real coroutine sync while pumping the EDT; retry one fixture cancellation,
     * then fail hard. This is the same sync entry point production uses on all three runtimes. */
    private fun awaitSync(contextFactory: () -> LoaderContext, timeoutMs: Long = 60_000) {
        repeat(2) { attempt ->
            try {
                val future = CompletableFuture<Unit>()
                val context = contextFactory()
                liveScope.launch {
                    try {
                        check(DataSourceSyncManager.getInstance().tryPerformSync(context, stopRunning = false, merge = false) != null) {
                            "Introspection returned no task"
                        }
                        check(context.errorHandler?.hasErrors() != true) { "Introspection reported errors" }
                        future.complete(Unit)
                    } catch (failure: Throwable) {
                        future.completeExceptionally(failure)
                    }
                }
                PlatformTestUtil.waitForFuture(future, timeoutMs)
                return
            } catch (t: Throwable) {
                val cancelled = generateSequence<Throwable>(t) { it.cause }
                    .any { it.javaClass.simpleName.contains("Cancell", true) }
                if (cancelled && attempt == 0) { println("(sync cancelled; retrying once)"); return@repeat }
                throw AssertionError("Enabled live introspection failed", t)
            }
        }
        error("Introspection did not finish")
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
        awaitSync({ LoaderContext.selectGeneralTask(project, ds) })
        pollUntil("first introspection", 30) { dbModelRoots(ds).isNotEmpty() }
        val defaultScope = ReadAction.compute<String, RuntimeException> {
            ds.introspectionScope?.let { TreePatternUtils.serialize(it) } ?: "<null>"
        }
        println("=== STAGE1 default connect: ${treeSnapshot(ds)} ; scope=$defaultScope ===")

        val tpch = requireNotNull(rootNamed(ds, "tpch")) { "Enabled live introspection did not enumerate tpch" }
        assertTrue(
            "MEASURED: catalogs enumerate on default connect (no interceptor needed) — tpch/memory/system",
            listOf("tpch", "memory", "system").all { rootNamed(ds, it) != null },
        )
        assertTrue("MEASURED: tpch has NO schemas on default connect (lazy work remains)", childrenOf(tpch).isEmpty())

        // STAGE 2 — CATALOG deepen tpch -> schemas load; tpch.tiny must stay table-less (one level).
        assertTrue("catalog deepen must kick a NEW introspection",
            requests.request(myFixture.file, ds, NamespaceTarget(listOf("tpch"), tpch)))
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
            requests.request(myFixture.file, ds, NamespaceTarget(listOf("tpch"), tpch)))

        // STAGE 3 — SCHEMA deepen tpch.tiny -> tables load.
        assertTrue("schema deepen must kick a NEW introspection",
            requests.request(myFixture.file, ds, NamespaceTarget(listOf("tpch", "tiny"), tiny)))
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
            requests.request(myFixture.file, ds, NamespaceTarget(listOf("tpch", "tiny"), tiny)))
    }

    fun testRealIdeCompletionReferenceUseHarvestAndDdlReload() {
        if (liveUrl.isNullOrBlank()) { println("SKIP: trino.live.url not set"); return }
        val seed = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            java.sql.DriverManager.getConnection(liveUrl).use { connection ->
                connection.createStatement().use { statement ->
                    for (schema in listOf("live_a", "live_b", "tiny")) statement.execute("CREATE SCHEMA IF NOT EXISTS memory.$schema")
                    statement.execute("CREATE TABLE IF NOT EXISTS memory.live_a.orders (orderkey bigint, a_only varchar)")
                    statement.execute("CREATE TABLE IF NOT EXISTS memory.live_b.orders (orderkey bigint, b_only bigint)")
                    statement.execute("CREATE TABLE IF NOT EXISTS memory.tiny.orders (orderkey bigint, memory_only varchar)")
                }
            }
        }
        PlatformTestUtil.waitForFuture(seed, 60_000)
        val ds = registerDataSource()
        awaitSync({ LoaderContext.selectGeneralTask(project, ds) })
        assertTrue(listOf("memory", "tpch").all { rootNamed(ds, it) != null })
        assertTrue("default catalog enumeration must stay shallow", childrenOf(requireNotNull(rootNamed(ds, "tpch"))).isEmpty())
        val file = myFixture.addFileToProject("live/console.sql", "SELECT 1")
        setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect, testRootDisposable)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        console = JdbcConsole.newConsole(project).forFile(file.virtualFile).fromDataSource(ds).build()
        val realConsole = requireNotNull(console)
        assertTrue(realConsole.isValid)
        com.intellij.database.DataBus.addRootAuditor(project, project.service<TrinoExecutionObserver>(), testRootDisposable)
        val settings = com.intellij.codeInsight.CodeInsightSettings.getInstance()
        val autocomplete = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        Disposer.register(testRootDisposable) { settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocomplete }
        myFixture.enableInspections(com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools()
            .single { it.shortName == "SqlResolveInspection" }.tool)

        setSql("SELECT * FROM memory.<caret>")
        complete()
        pollUntil("memory catalog completion loads schemas") { namespace(ds, "memory", "live_a") != null }
        assertTrue("schema enumeration must not cascade to tables", childrenOf(requireNotNull(namespace(ds, "memory", "live_a"))).isEmpty())
        assertTrue("another catalog stays unloaded", childrenOf(requireNotNull(rootNamed(ds, "tpch"))).isEmpty())
        assertTrue(complete().containsAll(listOf("live_a", "live_b", "tiny")))

        setSql("SELECT * FROM memory.live_a.<caret>")
        complete()
        pollUntil("schema completion loads orders") { table(ds, "memory", "live_a", "orders") != null }
        assertTrue(complete().contains("orders"))
        assertTrue("neighboring schema stays unloaded", childrenOf(requireNotNull(namespace(ds, "memory", "live_b"))).isEmpty())

        setSql("SELECT orderkey FROM memory.live_b.orders")
        highlightDuringRefresh()
        pollUntil("reference alone loads memory.live_b") { table(ds, "memory", "live_b", "orders") != null }
        setSql("SELECT orderkey FROM tpch.tiny.orders")
        highlightDuringRefresh()
        pollUntil("reference loads tpch schemas") { namespace(ds, "tpch", "tiny") != null }
        highlightDuringRefresh()
        pollUntil("reference loads tpch.tiny tables") { table(ds, "tpch", "tiny", "orders") != null }
        setSql("SELECT orderkey FROM memory.tiny.orders")
        highlightDuringRefresh()
        pollUntil("reference loads memory.tiny tables") { table(ds, "memory", "tiny", "orders") != null }

        execute(realConsole, "USE memory.live_a")
        pollUntil("USE changes the console namespace") { realConsole.currentNamespace?.name == "live_a" }
        assertEquals(listOf("memory", "live_a"), trinoDataSourceResolver.consoleNamespace(myFixture.file))
        setSql("SELECT o.<caret> FROM orders o")
        val first = complete()
        assertTrue(first.toString(), first.containsAll(listOf("orderkey", "a_only")))
        assertFalse(first.toString(), first.contains("b_only"))
        execute(realConsole, "USE memory.live_b")
        pollUntil("USE switches to the other same-named table") { realConsole.currentNamespace?.name == "live_b" }
        setSql("SELECT o.<caret> FROM orders o")
        val second = complete()
        assertTrue(second.toString(), second.containsAll(listOf("orderkey", "b_only")))
        assertFalse(second.toString(), second.contains("a_only"))
        for ((path, expected, rejected) in listOf(
            Triple("memory.tiny", "memory_only", "custkey"), Triple("tpch.tiny", "custkey", "memory_only"))) {
            execute(realConsole, "USE $path")
            pollUntil("current namespace is $path") { trinoDataSourceResolver.consoleNamespace(myFixture.file).joinToString(".") == path }
            setSql("SELECT o.<caret> FROM tiny.orders o")
            val items = complete()
            assertTrue(items.toString(), expected in items)
            assertFalse(items.toString(), rejected in items)
            setSql("SELECT orderkey, $expected FROM orders WHERE orderkey >= 0")
            assertNoFalseReports()
            execute(realConsole, myFixture.editor.document.text)
        }

        val harvest = com.intellij.openapi.application.ApplicationManager.getApplication().service<TrinoLiveCatalogService>()
        pollUntil("real IDE connection harvests SHOW FUNCTIONS") { harvest.selection(ds).source is CatalogSource.Live }
        assertTrue(harvest.completion(myFixture.file).functions.any { it.name == "abs" })
        execute(realConsole, "CREATE SCHEMA memory.live_reload")
        pollUntil("real CREATE SCHEMA reloads catalog") { namespace(ds, "memory", "live_reload") != null }
        execute(realConsole, "DROP SCHEMA memory.live_reload")
        pollUntil("real DROP SCHEMA reloads catalog") { namespace(ds, "memory", "live_reload") == null }
        println("LIVE IDE: catalogs, one-level completion/reference loading, console USE, same-named tables, SHOW FUNCTIONS and schema DDL passed")
    }

    private fun namespace(ds: LocalDataSource, catalog: String, schema: String) =
        rootNamed(ds, catalog)?.let(::childrenOf)?.firstOrNull { it.name == schema }

    private fun table(ds: LocalDataSource, catalog: String, schema: String, name: String) =
        namespace(ds, catalog, schema)?.let(::childrenOf)?.firstOrNull { it.name == name }

    private fun setSql(sql: String) {
        com.intellij.codeInsight.lookup.LookupManager.getInstance(project).hideActiveLookup()
        val offset = sql.indexOf("<caret>").takeIf { it >= 0 } ?: sql.length
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(sql.replace("<caret>", ""))
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        myFixture.editor.caretModel.moveToOffset(offset)
    }

    private fun complete(): List<String> {
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty().also {
            com.intellij.codeInsight.lookup.LookupManager.getInstance(project).hideActiveLookup()
        }
    }

    private fun highlightDuringRefresh() =
        com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.file,
            myFixture.editor, intArrayOf(), true)

    private fun assertNoFalseReports() {
        DbPsiFacade.getInstance(project).clearCaches()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val reports = highlightDuringRefresh().filter { it.severity >= com.intellij.lang.annotation.HighlightSeverity.WARNING }
        assertTrue(reports.map { it.description }.toString(), reports.isEmpty())
    }

    private fun execute(console: JdbcConsole, sql: String) {
        val finished = CompletableFuture<Unit>()
        setSql(sql)
        fun matches(context: DataRequest.Context) = context.query.trim().removeSuffix(";") == sql.trim().removeSuffix(";")
        console.session.messageBus.addAuditor(object : DataAuditor {
            override fun error(context: DataRequest.Context, error: com.intellij.database.connection.throwable.info.ErrorInfo) {
                if (matches(context)) finished.completeExceptionally(AssertionError("$sql: $error"))
            }
            override fun requestFinished(context: DataRequest.Context) {
                if (matches(context)) finished.complete(Unit)
            }
        })
        console.executeQueries(myFixture.editor, console.scriptModel, com.intellij.database.settings.DatabaseSettings.ExecOption())
        PlatformTestUtil.waitForFuture(finished, 60_000)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
}

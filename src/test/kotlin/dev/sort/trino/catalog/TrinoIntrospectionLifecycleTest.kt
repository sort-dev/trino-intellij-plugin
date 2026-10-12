package dev.sort.trino.catalog

import com.intellij.database.dataSource.DataSourceSyncManager
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dialects.postgres.model.PgDatabase
import com.intellij.database.dialects.postgres.model.PgRoot
import com.intellij.database.model.ModelFactory
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.ui.EditorNotificationProvider
import dev.brikk.house.sql.intellij.catalog.AutoIntrospectEngine
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.testing.HeldIntrospections
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.sort.trino.TrinoDbms
import dev.sort.trino.sql.TrinoSqlDialect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.CancellationException

class TrinoIntrospectionLifecycleTest : BasePlatformTestCase() {
    private lateinit var errors: BackgroundErrors
    private lateinit var introspections: HeldIntrospections
    private lateinit var scope: CoroutineScope
    private lateinit var requests: TrinoAutoIntrospectService
    private lateinit var notifier: TrinoIntrospectBannerProvider
    private lateinit var source: LocalDataSource
    private lateinit var catalog: PgDatabase

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
        introspections = HeldIntrospections(testRootDisposable)
        assertSame(TrinoCatalogProfile, project.service<TrinoAutoIntrospectService>().profile)
        scope = errors.newScope(testRootDisposable)
        requests = TrinoAutoIntrospectService(project, scope)
        project.registerOrReplaceServiceInstance(TrinoAutoIntrospectService::class.java, requests, testRootDisposable)
        notifier = EditorNotificationProvider.EP_NAME.getExtensions(project).filterIsInstance<TrinoIntrospectBannerProvider>().single()
        val model = ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK)
        catalog = (model.root as PgRoot).databases.createOrGet(1L).apply { name = "tpch" }
        source = LocalDataSource().apply {
            name = "Offline Trino introspection lifecycle"
            databaseDriver = requireNotNull(DatabaseDriverManager.getInstance().getDriver("trino-brikk"))
            url = "jdbc:offline:introspection-lifecycle"
            isAutoSynchronize = false
            isKeepAlive = false
            this.model = model
        }
        LocalDataSourceManager.getInstance(project).addDataSource(source)
        DbPsiFacade.getInstance(project).clearCaches()
        configure("SELECT * FROM tpch.<caret>")
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)
        assertSame(source, trinoDataSourceResolver.resolve(myFixture.file).dataSource)
        assertChildlessCatalog()
        assertNoBanner()
    }

    override fun tearDown() {
        RunAll(
            { introspections.finishAll() },
            { errors.awaitScopes() },
            { errors.assertNone() },
            { LocalDataSourceManager.getInstance(project).removeDataSource(source) },
            { DbPsiFacade.getInstance(project).clearCaches() },
            { super.tearDown() },
        ).run()
    }

    fun testSuccessfulRefreshClearsTheBanner() {
        finishRefresh { catalog.schemas.createOrGet("tiny") }
        assertEquals(AutoIntrospectEngine.State.Done, state())
    }

    fun testValidEmptyRefreshClearsTheBanner() {
        finishRefresh { }
        assertChildlessCatalog()
        assertInstanceOf(state(), AutoIntrospectEngine.State.Failed::class.java)
    }

    fun testFailedRefreshClearsTheBanner() {
        finishRefresh { throw java.sql.SQLException("offline") }
        assertChildlessCatalog()
        assertInstanceOf(state(), AutoIntrospectEngine.State.Failed::class.java)
    }

    fun testCancellationDuringRefreshClearsTheBanner() {
        finishRefresh { throw CancellationException() }
        assertChildlessCatalog()
        assertInstanceOf(state(), AutoIntrospectEngine.State.Failed::class.java)
    }

    fun testCancellationWhileRefreshIsSuspendedClearsTheBanner() {
        completeNamespace()
        val refresh = introspections.next()
        assertBanner("tpch")
        scope.cancel()
        refresh.finish()
        errors.awaitScopes()
        awaitFinished()
        assertInstanceOf(state(), AutoIntrospectEngine.State.Failed::class.java)
    }

    fun testOlderRefreshCannotClearANewerRefreshForTheEditor() {
        val memory = (catalog.model.root as PgRoot).databases.createOrGet(2L).apply { name = "memory" }
        completeNamespace()
        val older = introspections.next()
        configure("SELECT * FROM memory.<caret>")
        completeNamespace()
        assertBanner("memory")
        older.finish { catalog.schemas.createOrGet("tiny") }
        val newer = introspections.next()
        assertEquals(AutoIntrospectEngine.State.Done, state())
        assertBanner("memory")
        newer.finish { memory.schemas.createOrGet("web") }
        awaitFinished()
    }

    fun testRequestBannersAreBoundToTheirFiles() {
        completeNamespace()
        val refresh = introspections.next()
        val first = myFixture.file.virtualFile
        configure("SELECT 1<caret>", "other.sql")
        assertNoBanner()
        assertNotNull(notifier.collectNotificationData(project, first))
        refresh.finish { catalog.schemas.createOrGet("tiny") }
        errors.awaitScopes()
        assertNull(requests.pending(first))
        assertNull(notifier.collectNotificationData(project, first))
    }

    fun testQuotedCompletionTargetsTheTrinoModelName() {
        catalog.name = "tp\"ch.sales"
        configure("SELECT * FROM \"tp\"\"ch.sales\".<caret>")
        finishRefresh { catalog.schemas.createOrGet("tiny") }
        assertEquals(AutoIntrospectEngine.State.Done, state(listOf(catalog.name)))
    }

    fun testSchemaRefreshTargetsOnlyTheSchemaAndNeverATable() {
        val schema = catalog.schemas.createOrGet("tiny")
        configure("SELECT * FROM tpch.tiny.<caret>")
        completeNamespace()
        val refresh = introspections.next()
        assertSame(source, refresh.context.dataSource)
        assertEquals(1, refresh.context.countTasks())
        assertBanner("tpch.tiny")
        refresh.finish { schema.tables.createOrGet("orders") }
        awaitFinished()
        assertEquals(AutoIntrospectEngine.State.Done, state(listOf("tpch", "tiny")))
        configure("SELECT * FROM tpch.tiny.orders.<caret>")
        completeNamespace()
        introspections.assertNone()
        assertNoBanner()
    }

    fun testCommentsAndStringsDoNotTrigger() {
        for (text in listOf("-- tpch.<caret>", "SELECT 'tpch.<caret>'", "/* tpch.<caret> */")) {
            configure(text)
            completeNamespace()
            introspections.assertNone()
            assertNoBanner()
        }
    }

    fun testForeignConsoleDoesNotIntrospectTrino() {
        RecordingConsole(project, myFixture.file.virtualFile, "postgresql").use {
            completeNamespace()
            introspections.assertNone()
            assertNoBanner()
        }
    }

    fun testTrinoPathsStayFullyQualifiedDespiteTheConsoleNamespace() {
        catalog.schemas.createOrGet("tiny")
        RecordingConsole(project, myFixture.file.virtualFile, "trino-brikk").use { recording ->
            recording.console.dataSource.model = source.model
            DbPsiFacade.getInstance(project).clearCaches()
            recording.console.setSearchPath(SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE)))
            assertFalse(requests.profile.usesCurrentNamespace)
            assertFalse(requests.requestPath(myFixture.file, listOf("tiny")))
            introspections.assertNone()
            assertTrue(requests.requestPath(myFixture.file, listOf("tpch", "tiny")))
            introspections.next().finish { catalog.schemas.createOrGet("tiny").tables.createOrGet("orders") }
            awaitFinished()
        }
    }

    fun testCancellationDuringRequestPropagates() {
        RecordingConsole(project, myFixture.file.virtualFile, "trino-brikk").use { recording ->
            val requestingThread = Thread.currentThread()
            for (cancellation in listOf(ProcessCanceledException(), CancellationException())) {
                recording.beforeSessionCall = { if (Thread.currentThread() === requestingThread) throw cancellation }
                try {
                    assertSame(cancellation, runCatching { requests.requestPath(myFixture.file, listOf("tpch")) }.exceptionOrNull())
                } finally {
                    recording.beforeSessionCall = {}
                }
            }
            introspections.assertNone()
            assertNoBanner()
        }
    }

    private fun finishRefresh(refresh: () -> Unit) {
        completeNamespace()
        assertNotNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
        val held = introspections.next()
        assertSame(source, held.context.dataSource)
        assertEquals(1, held.context.countTasks())
        assertNotNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
        held.finish(refresh)
        awaitFinished()
        completeNamespace()
        introspections.assertNone()
        assertNoBanner()
    }

    private fun state(path: List<String> = listOf("tpch")) = requests.engine.state(AutoIntrospectEngine.Key(source.uniqueId, path))

    private fun awaitFinished() {
        PlatformTestUtil.waitWithEventsDispatching("Trino introspection finishes", {
            !DataSourceSyncManager.getInstance().isActive(source) && requests.pending(myFixture.file.virtualFile) == null
        }, 10)
        errors.awaitScopes()
        assertNoBanner()
    }

    private fun assertNoBanner() {
        assertNull(requests.pending(myFixture.file.virtualFile))
        assertNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
    }

    private fun assertBanner(namespace: String) {
        assertEquals(namespace, requests.pending(myFixture.file.virtualFile))
        assertNotNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
    }

    private fun configure(text: String, name: String = "introspect.sql") {
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
        myFixture.configureByText(name, text)
        setSqlDialectMapping(project, myFixture.file.virtualFile, TrinoSqlDialect, testRootDisposable)
    }

    private fun completeNamespace() {
        com.intellij.codeInsight.lookup.LookupManager.getInstance(project).hideActiveLookup()
        myFixture.completeBasic()
        com.intellij.codeInsight.lookup.LookupManager.getInstance(project).hideActiveLookup()
    }

    private fun assertChildlessCatalog() {
        val model = requireNotNull(DbPsiFacade.getInstance(project).findDataSource(source.uniqueId)).model
        val namespace = model.modelRoots.single { it.name == "tpch" }
        assertTrue(namespace.getDasChildren(ObjectKind.SCHEMA).none())
        assertTrue(namespace.getDasChildren(null).none())
    }
}

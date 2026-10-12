package dev.sort.trino.catalog

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.DataSourceSyncManager
import com.intellij.database.dialects.postgres.model.PgDatabase
import com.intellij.database.dialects.postgres.model.PgRoot
import com.intellij.database.model.ModelFactory
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.LoaderContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.sort.trino.sql.TrinoSqlDialect
import dev.sort.trino.TrinoDbms
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

class TrinoIntrospectionLifecycleTest : BasePlatformTestCase() {
    private val requests get() = project.service<TrinoIntrospectionService>()
    private val notifier = TrinoIntrospectNotifier()
    private lateinit var source: LocalDataSource
    private lateinit var catalog: PgDatabase

    override fun setUp() {
        super.setUp()
        TrinoAutoIntrospect.resetForTest()
        setSqlDialectMapping(project, null, TrinoSqlDialect)
        myFixture.configureByText("introspect.sql", "SELECT * FROM tpch.<caret>")
        setSqlDialectMapping(project, myFixture.file.virtualFile, TrinoSqlDialect)
        val model = ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK)
        catalog = (model.root as PgRoot).databases.createOrGet(1L).apply { name = "tpch" }
        source = LocalDataSource().apply {
            name = "Introspection lifecycle ${java.util.UUID.randomUUID()}"
            databaseDriver = requireNotNull(DatabaseDriverManager.getInstance().getDriver("trino-brikk"))
            url = "jdbc:offline:introspection-lifecycle"
            isAutoSynchronize = false
            isKeepAlive = false
            this.model = model
        }
        LocalDataSourceManager.getInstance(project).addDataSource(source)
        DbPsiFacade.getInstance(project).clearCaches()
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)
        assertSame(source, trinoDataSourceResolver.resolve(myFixture.file).dataSource)
        assertChildlessCatalog()
    }

    override fun tearDown() {
        try {
            TrinoAutoIntrospect.resetForTest()
            LocalDataSourceManager.getInstance(project).removeDataSource(source)
            DbPsiFacade.getInstance(project).clearCaches()
        } finally {
            super.tearDown()
        }
    }

    fun testSuccessfulRefreshClearsTheBanner() {
        finishRefresh { catalog.schemas.createOrGet("tiny") }
    }

    fun testValidEmptyRefreshClearsTheBanner() {
        finishRefresh { }
        assertChildlessCatalog()
    }

    fun testFailedRefreshClearsTheBanner() {
        finishRefresh { throw java.sql.SQLException("offline") }
        assertChildlessCatalog()
    }

    fun testCancellationDuringRefreshClearsTheBannerAndCancelsTheJob() {
        finishRefresh { throw CancellationException() }
        assertChildlessCatalog()
        val job = requests.schedule(myFixture.file.virtualFile, "tpch") { throw CancellationException() }
        await(job)
        assertTrue(job.isCancelled)
        assertNoPendingAfterCompletion()
    }

    fun testCancellationWhileRefreshIsSuspendedClearsTheBanner() {
        val gate = CompletableDeferred<Unit>()
        claimNamespace()
        val job = requests.schedule(myFixture.file.virtualFile, "tpch") { gate.await() }
        assertNotNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
        job.cancel()
        await(job)
        assertTrue(job.isCancelled)
        assertNoPendingAfterCompletion()
    }

    fun testOlderRefreshCannotClearANewerRefreshForTheEditor() {
        val file = myFixture.file.virtualFile
        claimNamespace()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val older = requests.schedule(file, "tpch") { first.await() }
        val newer = requests.schedule(file, "memory") { second.await() }
        try {
            first.complete(Unit)
            await(older)
            assertNotNull(notifier.collectNotificationData(project, file))
            second.complete(Unit)
            await(newer)
            assertNoPendingAfterCompletion()
        } finally {
            older.cancel()
            newer.cancel()
        }
    }

    private fun finishRefresh(refresh: () -> Unit) {
        val started = CompletableFuture<LoaderContext>()
        val release = CompletableFuture<Unit>()
        DataSourceSyncManager.withDefaultExecutor(testRootDisposable) { task, _, _, _ ->
            started.complete(task.context)
            release.get()
            refresh()
        }
        try {
            completeNamespace()
            assertSame(TrinoSqlDialect, myFixture.file.language)
            assertNotNull("Completion did not schedule a namespace refresh", notifier.collectNotificationData(project, myFixture.file.virtualFile))
            val context = PlatformTestUtil.waitForFuture(started, 10_000)
            assertSame(source, context.dataSource)
            assertNotNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
            release.complete(Unit)
            PlatformTestUtil.waitWithEventsDispatching("refresh completes", {
                !DataSourceSyncManager.getInstance().isActive(source) && requests.pending(myFixture.file.virtualFile) == null
            }, 10)
            assertNoPendingAfterCompletion()
        } finally {
            release.complete(Unit)
            PlatformTestUtil.waitWithEventsDispatching("introspection queue finishes", {
                !DataSourceSyncManager.getInstance().isActive(source)
            }, 10)
        }
    }

    private fun await(job: Job) {
        PlatformTestUtil.waitWithEventsDispatching("introspection finishes", { job.isCompleted }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun assertNoPendingAfterCompletion() {
        val file = myFixture.file.virtualFile
        assertNull(notifier.collectNotificationData(project, file))
        assertFalse(TrinoAutoIntrospect.request(project, source, TrinoNamespacePath.Level.CATALOG, "tpch", null, catalog, file))
        completeNamespace()
        assertNull(notifier.collectNotificationData(project, file))
    }

    private fun claimNamespace() {
        assertTrue(TrinoAutoIntrospect.claimOnce(TrinoAutoIntrospect.keyFor(source.uniqueId, TrinoNamespacePath.Level.CATALOG, "tpch", null)))
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

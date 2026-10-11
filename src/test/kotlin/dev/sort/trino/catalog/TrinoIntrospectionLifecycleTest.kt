package dev.sort.trino.catalog

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.trino.setSqlDialectMapping
import dev.sort.trino.sql.TrinoSqlDialect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import java.util.concurrent.CancellationException

class TrinoIntrospectionLifecycleTest : BasePlatformTestCase() {
    private val requests get() = project.service<TrinoIntrospectionService>()
    private val notifier = TrinoIntrospectNotifier()

    override fun setUp() {
        super.setUp()
        TrinoAutoIntrospect.resetForTest()
        myFixture.configureByText("introspect.sql", "SELECT * FROM tpch.<caret>")
        setSqlDialectMapping(project, myFixture.file.virtualFile, TrinoSqlDialect.INSTANCE)
    }

    override fun tearDown() {
        try {
            TrinoAutoIntrospect.resetForTest()
        } finally {
            super.tearDown()
        }
    }

    fun testSuccessfulRefreshClearsTheBanner() {
        finishRefresh { }
    }

    fun testValidEmptyRefreshClearsTheBanner() {
        val source = LocalDataSource.create("empty", "io.trino.jdbc.TrinoDriver", "jdbc:trino://localhost:8080", "")
        finishRefresh { assertTrue(source.model.modelRoots.none()) }
    }

    fun testFailedRefreshClearsTheBanner() {
        finishRefresh { throw java.sql.SQLException("offline") }
    }

    fun testCancellationDuringRefreshClearsTheBannerAndCancelsTheJob() {
        val job = finishRefresh { throw CancellationException() }
        assertTrue(job.isCancelled)
    }

    fun testCancellationWhileRefreshIsSuspendedClearsTheBanner() {
        val gate = CompletableDeferred<Unit>()
        val job = requests.schedule(myFixture.file.virtualFile, "tpch") { gate.await() }
        assertNotNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
        job.cancel()
        await(job)
        assertTrue(job.isCancelled)
        assertNoPendingAfterCompletion()
    }

    fun testOlderRefreshCannotClearANewerRefreshForTheEditor() {
        val file = myFixture.file.virtualFile
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

    private fun finishRefresh(refresh: suspend () -> Unit): Job {
        val gate = CompletableDeferred<Unit>()
        val job = requests.schedule(myFixture.file.virtualFile, "tpch") {
            gate.await()
            refresh()
        }
        try {
            assertNotNull(notifier.collectNotificationData(project, myFixture.file.virtualFile))
            gate.complete(Unit)
            await(job)
            assertNoPendingAfterCompletion()
            return job
        } finally {
            job.cancel()
        }
    }

    private fun await(job: Job) {
        PlatformTestUtil.waitWithEventsDispatching("introspection finishes", { job.isCompleted }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun assertNoPendingAfterCompletion() {
        val file = myFixture.file.virtualFile
        assertNull(notifier.collectNotificationData(project, file))
        val source = LocalDataSource.create("deduped", "io.trino.jdbc.TrinoDriver", "jdbc:trino://localhost:8080", "")
        TrinoAutoIntrospect.claimOnce(TrinoAutoIntrospect.keyFor(source.uniqueId, TrinoNamespacePath.Level.CATALOG, "tpch", null))
        assertFalse(TrinoAutoIntrospect.request(project, source, TrinoNamespacePath.Level.CATALOG, "tpch", null, null, file))
        myFixture.completeBasic()
        assertNull(notifier.collectNotificationData(project, file))
    }
}

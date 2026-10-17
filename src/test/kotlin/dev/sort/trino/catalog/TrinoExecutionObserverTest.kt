package dev.sort.trino.catalog

import com.intellij.database.dataSource.DataSourceSyncManager
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.dialects.postgres.model.PgDatabase
import com.intellij.database.dialects.postgres.model.PgRoot
import com.intellij.database.model.ModelFactory
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.database.model.ObjectKind
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import dev.brikk.house.sql.intellij.catalog.AutoIntrospectEngine
import dev.brikk.house.sql.intellij.catalog.ExecutionObserver
import dev.brikk.house.sql.intellij.catalog.ExecutionObserverProfile
import dev.brikk.house.sql.intellij.catalog.LiveCatalogService
import dev.brikk.house.sql.intellij.catalog.StatementRules
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.testing.HeldIntrospections
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.sort.trino.TrinoDbms

class TrinoExecutionObserverTest : BasePlatformTestCase() {
    private lateinit var errors: BackgroundErrors
    private lateinit var introspections: HeldIntrospections
    private lateinit var recording: RecordingConsole
    private lateinit var requests: TrinoAutoIntrospectService
    private lateinit var observer: TrinoExecutionObserver
    private lateinit var catalog: PgDatabase

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
        introspections = HeldIntrospections(testRootDisposable)
        assertSame(TrinoExecutionObserverProfile, project.service<TrinoExecutionObserver>().profile)
        requests = TrinoAutoIntrospectService(project, errors.newScope(testRootDisposable))
        project.registerOrReplaceServiceInstance(TrinoAutoIntrospectService::class.java, requests, testRootDisposable)
        observer = TrinoExecutionObserver(project, errors.newScope(testRootDisposable))
        myFixture.configureByText("observed.sql", "SELECT 1")
        recording = RecordingConsole(project, myFixture.file.virtualFile, "trino-brikk")
        val model = ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK)
        catalog = (model.root as PgRoot).databases.createOrGet(1L).apply {
            name = "memory"
            schemas.createOrGet("loaded")
        }
        (model.root as PgRoot).databases.createOrGet(2L).name = "unloaded"
        recording.console.dataSource.model = model
        DbPsiFacade.getInstance(project).clearCaches()
    }

    override fun tearDown() = RunAll(
        { introspections.finishAll() },
        { errors.awaitScopes() },
        { errors.assertNone() },
        { recording.close() },
        { super.tearDown() },
    ).run()

    fun testCreateSchemaReloadsLoadedCatalogWithoutChangingScope() {
        val source = recording.console.dataSource
        val scope = source.introspectionScope
        execute("CREATE SCHEMA memory.extra")
        val refresh = introspections.next()
        assertSame(source, refresh.context.dataSource)
        assertEquals(1, refresh.context.countTasks())
        refresh.finish { catalog.schemas.createOrGet("extra") }
        awaitIdle()
        assertSame(scope, source.introspectionScope)
        assertNull(requests.engine.state(AutoIntrospectEngine.Key(source.uniqueId, listOf("memory"))))
        assertNull(requests.pending(myFixture.file.virtualFile))
        assertTrue(catalog.getDasChildren(ObjectKind.SCHEMA).any { it.name == "extra" })
    }

    fun testDropSchemaReloadsAnEmptiedCatalogAgain() {
        execute("DROP SCHEMA memory.loaded")
        introspections.next().finish { catalog.schemas.remove { true } }
        awaitIdle()
        execute("CREATE SCHEMA memory.again")
        introspections.next().finish { catalog.schemas.createOrGet("again") }
        awaitIdle()
    }

    fun testCatalogDdlReloadsTopLevel() {
        for (sql in listOf("CREATE CATALOG fresh USING memory", "DROP CATALOG fresh")) {
            execute(sql)
            val refresh = introspections.next()
            assertEquals(1, refresh.context.countTasks())
            refresh.finish()
            awaitIdle()
        }
    }

    fun testUnqualifiedSchemaUsesConsoleCatalog() {
        recording.console.setSearchPath(SearchPath.of(ObjectPath.create("memory", ObjectKind.DATABASE)))
        execute("CREATE SCHEMA extra")
        introspections.next().finish()
        awaitIdle()
    }

    fun testUnloadedNamespacesAndNonDdlDoNotReload() {
        execute("CREATE SCHEMA unloaded.extra")
        execute("SELECT 'CREATE SCHEMA memory.extra'")
        execute("CREATE FUNCTION memory.loaded.f() RETURNS bigint RETURN 1")
        errors.awaitScopes()
        introspections.assertNone()
        assertNull(TrinoExecutionObserverProfile.liveCatalog)
    }

    fun testRoutineBodiesDoNotEndAnExplicitTrinoTransaction() {
        assertSame(StatementRules.TRINO_TRANSACTIONS, TrinoExecutionObserverProfile.transactionStatements)
        val live = TrinoLiveCatalogService(errors.newScope(testRootDisposable))
        ApplicationManager.getApplication().registerOrReplaceServiceInstance(TrinoLiveCatalogService::class.java, live, testRootDisposable)
        val tracking = object : ExecutionObserver(project, errors.newScope(testRootDisposable)) {
            override val profile = object : ExecutionObserverProfile by TrinoExecutionObserverProfile {
                override val liveCatalog = TrinoLiveCatalogService::class.java
            }
        }
        val transactions = LiveCatalogService::class.java.getDeclaredField("explicitTransactions").apply { isAccessible = true }.get(live) as Set<*>
        val session = recording.console.session
        fun run(sql: String) {
            val console = recording.console
            val query = object : DataRequest.QueryRequest(console, sql, DataRequest.newConstraints(), null) {}
            tracking.requestFinished(DataRequest.Context(console.session.messageBus.dataProducer, DataRequest.newOutputHandler(), query, DataRequest.AUTO_COMMIT))
        }
        assertFalse(transactions.contains(session))
        run("START TRANSACTION")
        assertTrue(transactions.contains(session))
        run("CREATE FUNCTION f() RETURNS bigint BEGIN RETURN 1; END")
        assertTrue(transactions.contains(session))
        run("BEGIN; END")
        assertTrue(transactions.contains(session))
        run("COMMIT")
        assertFalse(transactions.contains(session))
        run("CREATE FUNCTION f() RETURNS bigint BEGIN RETURN 1; END")
        assertFalse(transactions.contains(session))
        run("START TRANSACTION; CREATE FUNCTION f() RETURNS bigint BEGIN RETURN 1; END")
        assertTrue(transactions.contains(session))
        run("ROLLBACK")
        assertFalse(transactions.contains(session))
    }

    private fun execute(sql: String) {
        val console = recording.console
        val query = object : DataRequest.QueryRequest(console, sql, DataRequest.newConstraints(), null) {}
        observer.requestFinished(DataRequest.Context(console.session.messageBus.dataProducer, DataRequest.newOutputHandler(), query, DataRequest.AUTO_COMMIT))
    }

    private fun awaitIdle() {
        PlatformTestUtil.waitWithEventsDispatching("Trino namespace reload finishes", {
            !DataSourceSyncManager.getInstance().isActive(recording.console.dataSource)
        }, 10)
        errors.awaitScopes()
    }
}

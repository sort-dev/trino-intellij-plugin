package dev.sort.trino.catalog

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionInterceptor
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.remote.jdbc.helpers.GenericJdbcHelper
import com.intellij.database.remote.jdbc.impl.RemoteConnectionImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import dev.brikk.house.sql.intellij.catalog.CatalogSource
import dev.brikk.house.sql.intellij.catalog.TrinoFunctionHarvester
import dev.brikk.house.sql.intellij.completion.completableFunctions
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.testing.JdbcCatalogRows
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.metadata.FunctionPresentation
import dev.brikk.house.sql.metadata.TRINO_METADATA
import dev.sort.trino.TrinoDbms
import dev.sort.trino.sql.TrinoCompletionProfile
import dev.sort.trino.sql.TrinoSqlDialect
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class TrinoLiveCatalogTest : BasePlatformTestCase() {
    private lateinit var errors: BackgroundErrors
    private lateinit var catalog: TrinoLiveCatalogService
    private lateinit var recording: RecordingConsole
    private val queries = CopyOnWriteArrayList<String>()
    private val cancelled = AtomicBoolean()
    private var answer: (String) -> List<List<String?>> = ::rows

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
        val application = ApplicationManager.getApplication()
        assertSame(TrinoFunctionCatalogProfile, application.service<TrinoLiveCatalogService>().profile)
        catalog = TrinoLiveCatalogService(errors.newScope(testRootDisposable))
        application.registerOrReplaceServiceInstance(TrinoLiveCatalogService::class.java, catalog, testRootDisposable)
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
        myFixture.configureByText("live-functions.sql", "SELECT <caret>")
        recording = RecordingConsole(project, myFixture.file.virtualFile, "trino-brikk")
        setSqlDialectMapping(project, myFixture.file.virtualFile, TrinoSqlDialect, testRootDisposable)
        assertSame(TrinoDbms.TRINO_BRIKK, recording.console.dataSource.dbms)
    }

    override fun tearDown() = RunAll(
        { errors.awaitScopes() },
        { errors.assertNone() },
        { recording.close() },
        { super.tearDown() },
    ).run()

    fun testWithoutHarvestCompletionEqualsBundled() {
        assertBundled()
        assertEquals(TRINO_METADATA.default.completableFunctions().single { it.name == "abs" }, offered("ab").single { it.name == "abs" })
        assertTrue(queries.isEmpty())
    }

    fun testDumpThroughTestKitEqualsBundled() {
        val harvest = requireNotNull(TrinoFunctionHarvester.harvest(JdbcCatalogRows(jdbc())))
        assertEquals("483", harvest.shared.serverVersion)
        assertEquals(TRINO_METADATA.default.functions.functions.toSet(), harvest.shared.functions.map { it.def }.toSet())
    }

    fun testHarvestAddsServerFunctionKeepsGrammarAndWinsSignatures() {
        answer = { sql ->
            rows(sql).let { values ->
                if (sql == TrinoFunctionHarvester.SQL_FUNCTIONS) {
                    values.filterNot { it.first() == "abs" } + listOf(
                        listOf("server_only", "bigint", "bigint", "scalar"),
                        listOf("abs", "decimal(38, 0)", "decimal(38, 0)", "scalar"),
                    )
                } else values
            }
        }
        connect()
        assertInstanceOf(catalog.selection(recording.console.dataSource).source, CatalogSource.Live::class.java)
        assertTrue(offered("server_").any { it.name == "server_only" })
        assertEquals("decimal(38, 0)", offered("ab").single { it.name == "abs" }.returnType)
        assertTrue(offered("ca").any { it.name == "CAST" })
        assertTrue(offered("current_time").any { it.name == "CURRENT_TIMESTAMP" })
        assertTrue(offered("json_").any { it.name == "JSON_TABLE" })
        assertEquals(catalog.completion(myFixture.file).serverOnlyFunctions, TrinoCompletionProfile.serverOnlyFunctions(myFixture.file))
        connect()
        assertEquals(listOf("SELECT version()", "SHOW FUNCTIONS"), queries.toList())
    }

    fun testFailedHarvestLeavesBundledAndNextConnectionRetries() {
        answer = { throw SQLException("offline") }
        connect()
        assertBundled()
        answer = ::rows
        connect()
        assertInstanceOf(catalog.selection(recording.console.dataSource).source, CatalogSource.Live::class.java)
        assertEquals(2, queries.count { it == "SELECT version()" })
    }

    fun testTimedOutHarvestLeavesBundledAndCancelsStatement() {
        answer = { Thread.sleep(30_000); rows(it) }
        connect()
        assertTrue(cancelled.get())
        assertBundled()
        answer = ::rows
        connect()
        assertInstanceOf(catalog.selection(recording.console.dataSource).source, CatalogSource.Live::class.java)
    }

    fun testChangedIdentityReturnsToBundled() {
        connect()
        recording.console.dataSource.url = "jdbc:offline:another-server"
        assertBundled()
    }

    private fun assertBundled() {
        assertEquals(CatalogSource.Bundled, catalog.selection(recording.console.dataSource).source)
        assertEquals(TRINO_METADATA.default.completableFunctions(), TrinoCompletionProfile.functions(myFixture.file))
        assertTrue(TrinoCompletionProfile.serverOnlyFunctions(myFixture.file).isEmpty())
    }

    private fun offered(prefix: String): List<FunctionPresentation> {
        LookupManager.getInstance(project).hideActiveLookup()
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("SELECT $prefix") }
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        val functions = myFixture.completeBasic().orEmpty().mapNotNull { it.`object` as? FunctionPresentation }
        LookupManager.getInstance(project).hideActiveLookup()
        return functions
    }

    private fun rows(sql: String): List<List<String?>> = when (sql) {
        TrinoFunctionHarvester.SQL_VERSION -> listOf(listOf("483"))
        TrinoFunctionHarvester.SQL_FUNCTIONS -> requireNotNull(javaClass.getResourceAsStream("/trino-functions-483.tsv"))
            .bufferedReader().use { reader -> reader.readLines().filter(String::isNotBlank).map { it.split('\t') } }
        else -> error("Unexpected SQL: $sql")
    }

    private fun connect() {
        val remote = RemoteConnectionImpl(jdbc(), GenericJdbcHelper())
        val connection = proxy<DatabaseConnection> { name, _ ->
            when (name) {
                "getRemoteConnection" -> remote
                "getConnectionPoint" -> recording.console.dataSource
                "getDbms" -> TrinoDbms.TRINO_BRIKK
                "getRequestor" -> ConnectionRequestor.Anonymous()
                else -> error("Unexpected connection call: $name")
            }
        }
        val proto = proxy<DatabaseConnectionInterceptor.ProtoConnection> { name, _ ->
            when (name) {
                "getConnectionPoint" -> recording.console.dataSource
                else -> error("Unexpected proto call: $name")
            }
        }
        val interceptor = DatabaseConnectionInterceptor.EP_NAME.extensionList.filterIsInstance<TrinoHarvestConnectionInterceptor>().single()
        runBlocking {
            assertTrue(interceptor.interceptConnection(proto, silent = true))
            interceptor.handleConnected(connection, proto)
        }
    }

    private fun jdbc(): Connection = proxy { name, _ ->
        when (name) {
            "createStatement" -> proxy<Statement> { call, args ->
                when (call) {
                    "executeQuery" -> {
                        val sql = args[0] as String
                        queries += sql
                        val values = answer(sql)
                        var index = -1
                        proxy<ResultSet> { rowCall, columns ->
                            when (rowCall) {
                                "next" -> ++index < values.size
                                "getString" -> values[index].getOrNull((columns[0] as Int) - 1)
                                "close" -> null
                                else -> error("Unexpected result call: $rowCall")
                            }
                        }
                    }
                    "cancel" -> { cancelled.set(true); null }
                    "setQueryTimeout", "close" -> null
                    else -> error("Unexpected statement call: $call")
                }
            }
            "close" -> null
            else -> error("Unexpected JDBC call: $name")
        }
    }
}

private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
        when (method.name) {
            "equals" -> instance === args?.get(0)
            "hashCode" -> System.identityHashCode(instance)
            "toString" -> "Trino catalog fixture"
            else -> call(method.name, args.orEmpty())
        }
    } as T

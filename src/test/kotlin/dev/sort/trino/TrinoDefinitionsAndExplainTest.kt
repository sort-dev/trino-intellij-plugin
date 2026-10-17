package dev.sort.trino

import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.remote.jdbc.helpers.GenericJdbcHelper
import com.intellij.database.remote.jdbc.impl.RemoteConnectionImpl
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.catalog.ExplainOutcome
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.brikk.house.sql.intellij.catalog.TextExplainService
import dev.sort.trino.catalog.TrinoTextExplainProfile
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement

class TrinoDefinitionsAndExplainTest : BasePlatformTestCase() {
    private val submitted = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var opened = 0

    private fun service(errors: BackgroundErrors) = object : TextExplainService(project, errors.newScope(testRootDisposable)) {
        override val profile = TrinoTextExplainProfile
        override suspend fun <T> withHelperConnection(console: com.intellij.database.console.JdbcConsole,
            use: suspend (DatabaseConnectionCore) -> T): T {
            opened++
            return use(connection("Query Plan", "plan row 1\nplan row 2"))
        }
    }

    fun testExplainUsesHelperNamespaceAndNeverTheConsoleSession() {
        val file = myFixture.configureByText("explain.sql", "SELECT abs(-1) FROM t")
        val errors = BackgroundErrors(testRootDisposable)
        RecordingConsole(project, file.virtualFile, "trino-brikk").use { recording ->
            recording.console.setSearchPath(SearchPath.of(ObjectPath.create("a\"b", ObjectKind.DATABASE).append("c.d", ObjectKind.SCHEMA)))
            val explain = service(errors)
            assertTrue(explain.owns(recording.console))
            assertEquals(ExplainOutcome.Plan("plan row 1\nplan row 2"), runBlocking { explain.explain(recording.console, file.text) })
            assertEquals(listOf("USE \"a\"\"b\".\"c.d\"", "EXPLAIN SELECT abs(-1) FROM t"), submitted.toList())
            assertEquals(1, opened)
            assertEmpty(recording.requests)
            assertFalse(recording.console.session.isConnected)
        }
        errors.awaitScopes()
        errors.assertNone()
    }

    fun testUnsafeStatementsAndIncompleteNamespaceOpenNoHelper() {
        val file = myFixture.configureByText("refuse-explain.sql", "SELECT 1")
        val errors = BackgroundErrors(testRootDisposable)
        RecordingConsole(project, file.virtualFile, "trino-brikk").use { recording ->
            val explain = service(errors)
            for (sql in listOf("INSERT INTO t VALUES (1)", "CREATE TABLE t AS SELECT 1", "EXPLAIN ANALYZE SELECT 1",
                "SELECT :id", "SELECT 1; SELECT 2")) {
                assertInstanceOf(runBlocking { explain.explain(recording.console, sql) }, ExplainOutcome.Refused::class.java)
            }
            recording.console.setSearchPath(SearchPath.of(ObjectPath.create("lake", ObjectKind.DATABASE)))
            assertInstanceOf(runBlocking { explain.explain(recording.console, "SELECT 1") }, ExplainOutcome.Refused::class.java)
            assertEquals(0, opened)
            assertEmpty(submitted)
            assertEmpty(recording.requests)
        }
        errors.awaitScopes()
        errors.assertNone()
    }

    fun testExplainLeavesFunctionResolutionToTrino() {
        val file = myFixture.configureByText("functions-explain.sql", "SELECT custom_function(1)")
        val errors = BackgroundErrors(testRootDisposable)
        RecordingConsole(project, file.virtualFile, "trino-brikk").use { recording ->
            val explain = service(errors)
            for (sql in listOf(file.text, "SELECT hive.web.abs(1)")) {
                assertInstanceOf(runBlocking { explain.explain(recording.console, sql) }, ExplainOutcome.Plan::class.java)
            }
            assertEquals(listOf("EXPLAIN SELECT custom_function(1)", "EXPLAIN SELECT hive.web.abs(1)"), submitted.toList())
            assertEmpty(recording.requests)
        }
        errors.awaitScopes()
        errors.assertNone()
    }

    fun testShowCreateReadsAllSupportedDefinitionsWithQuotedPaths() {
        val catalog = obj("a\"b", ObjectKind.DATABASE)
        val schema = obj("c.d", ObjectKind.SCHEMA, catalog)
        val provider = TrinoDefinitionProvider()
        for ((kind, sqlKind) in listOf(ObjectKind.TABLE to "TABLE", ObjectKind.VIEW to "VIEW", ObjectKind.MAT_VIEW to "MATERIALIZED VIEW")) {
            val definition = obj("select", kind, schema)
            val sql = "SHOW CREATE $sqlKind \"a\"\"b\".\"c.d\".\"select\""
            assertTrue(provider.isSupported(definition))
            assertEquals(sql, provider.query(definition)!!.sql)
            val loaded = StringBuilder()
            provider.load(listOf(definition), connection("Create $sqlKind", "CREATE $sqlKind result"), loaded)
            assertTrue(loaded.toString(), loaded.contains("CREATE $sqlKind result"))
        }
        assertEquals("SHOW CREATE SCHEMA \"a\"\"b\".\"c.d\"", provider.query(schema)!!.sql)
        val schemaSources = StringBuilder()
        provider.load(listOf(schema), connection("Create Schema", "CREATE SCHEMA result"), schemaSources)
        assertTrue(schemaSources.toString(), schemaSources.contains("CREATE SCHEMA result"))
        assertFalse(provider.isSupported(obj("routine", ObjectKind.ROUTINE, schema)))
        assertFalse(provider.isSupported(obj("incomplete", ObjectKind.TABLE)))
        assertEquals(4, submitted.size)
        assertTrue(submitted.all { it.startsWith("SHOW CREATE ") })
    }

    private fun obj(name: String, kind: ObjectKind, parent: DasObject? = null): DasObject = boundary { operation, _ ->
        when (operation) {
            "getName" -> name
            "getKind" -> kind
            "getDasParent" -> parent
            else -> error("Unexpected model call: $operation")
        }
    }

    private fun connection(label: String, value: String): DatabaseConnectionCore {
        val jdbc = boundary<Connection> { operation, _ ->
            when (operation) {
                "createStatement" -> boundary<Statement> { call, args ->
                    when (call) {
                        "execute" -> { submitted += args[0] as String; false }
                        "executeQuery" -> {
                            submitted += args[0] as String
                            var next = true
                            boundary<ResultSet> { action, _ ->
                                when (action) {
                                    "next" -> next.also { next = false }
                                    "getString" -> value
                                    "getMetaData" -> boundary<ResultSetMetaData> { metadata, _ ->
                                        when (metadata) {
                                            "getColumnCount" -> 1
                                            "getColumnLabel" -> label
                                            else -> error("Unexpected metadata call: $metadata")
                                        }
                                    }
                                    "close" -> null
                                    else -> error("Unexpected result call: $action")
                                }
                            }
                        }
                        "setQueryTimeout", "close", "cancel" -> null
                        else -> error("Unexpected statement call: $call")
                    }
                }
                else -> error("Unexpected connection call: $operation")
            }
        }
        val remote = RemoteConnectionImpl(jdbc, GenericJdbcHelper())
        return boundary { operation, _ ->
            when (operation) {
                "getRemoteConnection" -> remote
                else -> error("Unexpected database connection call: $operation")
            }
        }
    }

    private inline fun <reified T> boundary(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
            when (method.name) {
                "equals" -> instance === args?.get(0)
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "Trino recording boundary"
                else -> call(method.name, args.orEmpty())
            }
        } as T
}

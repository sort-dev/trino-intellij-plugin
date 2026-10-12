package dev.sort.trino

import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.dialects.DatabaseDialect
import com.intellij.database.dialects.DatabaseDialectEx
import com.intellij.database.model.ObjectKind
import com.intellij.database.remote.jdbc.helpers.GenericJdbcHelper
import com.intellij.database.remote.jdbc.impl.RemoteConnectionImpl
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.sort.trino.catalog.TrinoCatalogProfile
import dev.sort.trino.sql.TrinoSqlDialect
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Statement

class TrinoConsoleNamespaceTest : BasePlatformTestCase() {
    private val queries = mutableListOf<String>()
    private var row = listOf<String?>("lake", "web")
    private var failure = false
    private val dialect get() = requireNotNull(DatabaseDialect.EP.forDbms(TrinoDbms.TRINO_BRIKK)) as DatabaseDialectEx

    fun testRegisteredDialectReadsBackAndUpdatesTheConsoleNamespace() {
        val file = myFixture.configureByText("namespace.sql", "SELECT 1")
        setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect, testRootDisposable)
        RecordingConsole(project, file.virtualFile, "trino-brikk").use { recording ->
            val connection = connection()
            for (names in listOf(listOf("lake", "web"), listOf("other", "analytics"), listOf("a\"b", "c.d"))) {
                row = names
                val expected = ObjectPath.create(names[0], ObjectKind.DATABASE).append(names[1], ObjectKind.SCHEMA)
                val readBack = requireNotNull(dialect.tryToLoadSearchPath(connection))
                assertEquals(expected, readBack.current)
                recording.console.setSearchPath(readBack)
                assertEquals(expected, recording.console.currentNamespace)
                assertEquals("USE \"${names[0].replace("\"", "\"\"")}\".\"${names[1].replace("\"", "\"\"")}\"", dialect.sqlSetSearchPath(readBack))
            }
            assertEquals(List(3) { "SELECT current_catalog AS catalog, current_schema AS schema" }, queries)
            assertEmpty(recording.requests)
        }
    }

    fun testIncompleteOrUnavailableNamespacesStayUnknownAndOutOfScopeStaysOff() {
        assertFalse(TrinoCatalogProfile.outOfScopeReferences)
        val connection = connection()
        for (names in listOf(listOf("lake", null), listOf(null, "web"), listOf("", "web"), listOf("lake", ""))) {
            row = names
            assertNull(dialect.tryToLoadSearchPath(connection))
        }
        failure = true
        assertNull(dialect.tryToLoadSearchPath(connection))
        assertNull(dialect.sqlSetSearchPath(SearchPath.of(ObjectPath.create("lake", ObjectKind.DATABASE))))
        assertNull(dialect.sqlSetSearchPath(SearchPath.of(ObjectPath.create("web", ObjectKind.SCHEMA))))
    }

    private fun connection(): DatabaseConnectionCore {
        val jdbc = boundary<Connection> { name, _ ->
            when (name) {
                "createStatement" -> boundary<Statement> { call, args ->
                    when (call) {
                        "executeQuery" -> {
                            queries += args[0] as String
                            if (failure) throw SQLException("offline")
                            var next = true
                            boundary<ResultSet> { operation, values ->
                                when (operation) {
                                    "next" -> next.also { next = false }
                                    "getString" -> row[(values[0] as Int) - 1]
                                    "getMetaData" -> boundary<ResultSetMetaData> { metadata, columns ->
                                        when (metadata) {
                                            "getColumnCount" -> 2
                                            "getColumnLabel" -> listOf("CATALOG", "schema")[(columns[0] as Int) - 1]
                                            else -> error("Unexpected metadata call: $metadata")
                                        }
                                    }
                                    "close" -> null
                                    else -> error("Unexpected result call: $operation")
                                }
                            }
                        }
                        "close" -> null
                        else -> error("Unexpected statement call: $call")
                    }
                }
                else -> error("Unexpected connection call: $name")
            }
        }
        val remote = RemoteConnectionImpl(jdbc, GenericJdbcHelper())
        return boundary { name, _ ->
            when (name) {
                "getRemoteConnection" -> remote
                else -> error("Unexpected database connection call: $name")
            }
        }
    }

    private inline fun <reified T> boundary(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
            when (method.name) {
                "equals" -> instance === args?.get(0)
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "Trino namespace fixture"
                else -> call(method.name, args.orEmpty())
            }
        } as T
}

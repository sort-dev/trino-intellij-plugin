package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.database.dialects.DatabaseDialect
import com.intellij.database.dialects.postgres.plan.PgPlanModelBuilder
import com.intellij.database.plan.ExplainPlanProvider
import com.intellij.database.plan.PlanRetrievalException
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.database.util.Version
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.sql.DriverManager
import java.sql.SQLException

class TrinoDatabaseFallbackTest : BasePlatformTestCase() {
    fun testExplainPlanIsUnavailableForTrino() {
        val provider = requireNotNull(ExplainPlanProvider.EP.forDbms(TrinoDbms.TRINO_BRIKK))
        for (analyze in listOf(false, true)) {
            assertFalse(provider.isSupported(Version.of(483), analyze))
            assertFalse(provider.isRawSupported(Version.of(483), analyze))
        }
        assertTrue(requireNotNull(ExplainPlanProvider.EP.forDbms(Dbms.POSTGRES)).isSupported(Version.of(17), false))
    }

    fun testLivePostgresFallbackCannotHandleTrino() {
        val url = System.getProperty("trino.live.url") ?: return
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("EXPLAIN (FORMAT JSON) SELECT * FROM tpch.tiny.orders").use { result ->
                    assertTrue(result.next())
                    val builder = PgPlanModelBuilder()
                    val data = builder.createData().apply { load(requireNotNull(result.getString(1))) }
                    try {
                        builder.build(data)
                        fail("PostgreSQL's plan reader accepted Trino's plan")
                    } catch (_: PlanRetrievalException) {
                        println("T4 reproduced: PostgreSQL's plan reader rejects the live Trino plan")
                    }
                }
                val dialect = requireNotNull(DatabaseDialect.EP.forDbms(Dbms.POSTGRES))
                val path = SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE).append("tiny", ObjectKind.SCHEMA))
                val switch = dialect.javaClass.getMethod("sqlSetSearchPath", SearchPath::class.java).invoke(dialect, path) as String
                val readBack = dialect.javaClass.superclass.getDeclaredMethod("getSearchPathQuery").apply { isAccessible = true }
                    .invoke(dialect) as String
                for (sql in listOf(switch, readBack)) {
                    try {
                        statement.execute(sql)
                        fail("PostgreSQL namespace SQL unexpectedly worked: $sql")
                    } catch (_: SQLException) {
                        println("T5 reproduced: live Trino rejects $sql")
                    }
                }
            }
        }
    }

    fun testNamespaceSwitchUsesTheSelectedCatalogAndSchema() {
        val dialect = requireNotNull(DatabaseDialect.EP.forDbms(TrinoDbms.TRINO_BRIKK))
        val path = SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE).append("tiny", ObjectKind.SCHEMA))
        val sql = dialect.javaClass.getMethod("sqlSetSearchPath", SearchPath::class.java).invoke(dialect, path) as String
        val parsed = io.trino.sql.parser.SqlParser().createStatement(sql) as io.trino.sql.tree.Use
        assertEquals("tpch", parsed.catalog.orElseThrow().value)
        assertEquals("tiny", parsed.schema.value)
        val url = System.getProperty("trino.live.url") ?: return
        val readBack = dialect.javaClass.getDeclaredMethod("getSearchPathQuery").apply { isAccessible = true }
            .invoke(dialect) as String
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(sql)
                statement.executeQuery(readBack).use { result ->
                    assertTrue(result.next())
                    assertEquals("tpch", result.getString("catalog"))
                    assertEquals("tiny", result.getString("schema"))
                }
            }
        }
    }

    fun testNamespaceSwitchQuotesIdentifiersAndRefusesIncompletePaths() {
        val dialect = requireNotNull(DatabaseDialect.EP.forDbms(TrinoDbms.TRINO_BRIKK))
        val switch = dialect.javaClass.getMethod("sqlSetSearchPath", SearchPath::class.java)
        val path = SearchPath.of(ObjectPath.create("a\"b", ObjectKind.DATABASE).append("c.d", ObjectKind.SCHEMA))
        val parsed = io.trino.sql.parser.SqlParser().createStatement(switch.invoke(dialect, path) as String) as io.trino.sql.tree.Use
        assertEquals("a\"b", parsed.catalog.orElseThrow().value)
        assertEquals("c.d", parsed.schema.value)
        assertNull(switch.invoke(dialect, SearchPath.of(ObjectPath.create("tiny", ObjectKind.SCHEMA))))
        assertNull(switch.invoke(dialect, SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE))))
    }
}

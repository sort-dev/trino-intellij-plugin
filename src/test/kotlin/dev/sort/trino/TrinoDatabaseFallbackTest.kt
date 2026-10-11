package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.database.dialects.DatabaseDialect
import com.intellij.database.dialects.DatabaseDialectEx
import com.intellij.database.dialects.postgres.plan.PgPlanModelBuilder
import com.intellij.database.plan.ExplainPlanProvider
import com.intellij.database.plan.PlanRetrievalException
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.database.util.Version
import com.intellij.testFramework.ApplicationRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.sql.DriverManager
import java.sql.SQLException

class TrinoDatabaseFallbackTest {
    @get:Rule val application = ApplicationRule()

    @Test fun testExplainPlanIsUnavailableForTrino() {
        val provider = requireNotNull(ExplainPlanProvider.EP.forDbms(TrinoDbms.TRINO_BRIKK))
        for (analyze in listOf(false, true)) {
            assertFalse(provider.isSupported(Version.of(483), analyze))
            assertFalse(provider.isRawSupported(Version.of(483), analyze))
        }
        assertTrue(requireNotNull(ExplainPlanProvider.EP.forDbms(Dbms.POSTGRES)).isSupported(Version.of(17), false))
    }

    @Test fun testLivePostgresFallbackCannotHandleTrino() {
        val url = System.getProperty("trino.live.url")
        assumeTrue("Live Trino URL not configured", !url.isNullOrBlank())
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("EXPLAIN (FORMAT JSON) SELECT * FROM tpch.tiny.orders").use { result ->
                    assertTrue(result.next())
                    val builder = PgPlanModelBuilder()
                    val data = builder.createData().apply { load(requireNotNull(result.getString(1))) }
                    try {
                        builder.build(data)
                        fail("PostgreSQL's plan reader accepted Trino's plan")
                    } catch (_: PlanRetrievalException) { }
                }
                val dialect = requireNotNull(DatabaseDialect.EP.forDbms(Dbms.POSTGRES)) as DatabaseDialectEx
                val path = SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE).append("tiny", ObjectKind.SCHEMA))
                val switch = requireNotNull(dialect.sqlSetSearchPath(path))
                val readBack = dialect.javaClass.superclass.getDeclaredMethod("getSearchPathQuery").apply { isAccessible = true }
                    .invoke(dialect) as String
                for (sql in listOf(switch, readBack)) {
                    try {
                        statement.execute(sql)
                        fail("PostgreSQL namespace SQL unexpectedly worked: $sql")
                    } catch (_: SQLException) { }
                }
            }
        }
    }

    @Test fun testNamespaceSwitchUsesTheSelectedCatalogAndSchema() {
        val dialect = requireNotNull(DatabaseDialect.EP.forDbms(TrinoDbms.TRINO_BRIKK)) as DatabaseDialectEx
        val path = SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE).append("tiny", ObjectKind.SCHEMA))
        val sql = requireNotNull(dialect.sqlSetSearchPath(path))
        val parsed = io.trino.sql.parser.SqlParser().createStatement(sql) as io.trino.sql.tree.Use
        assertEquals("tpch", parsed.catalog.orElseThrow().value)
        assertEquals("tiny", parsed.schema.value)
    }

    @Test fun testLiveNamespaceSwitchReadsBackTheSelectedCatalogAndSchema() {
        val url = System.getProperty("trino.live.url")
        assumeTrue("Live Trino URL not configured", !url.isNullOrBlank())
        val dialect = requireNotNull(DatabaseDialect.EP.forDbms(TrinoDbms.TRINO_BRIKK)) as DatabaseDialectEx
        val path = SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE).append("tiny", ObjectKind.SCHEMA))
        val sql = requireNotNull(dialect.sqlSetSearchPath(path))
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

    @Test fun testNamespaceSwitchQuotesIdentifiersAndRefusesIncompletePaths() {
        val dialect = requireNotNull(DatabaseDialect.EP.forDbms(TrinoDbms.TRINO_BRIKK)) as DatabaseDialectEx
        val path = SearchPath.of(ObjectPath.create("a\"b", ObjectKind.DATABASE).append("c.d", ObjectKind.SCHEMA))
        val parsed = io.trino.sql.parser.SqlParser().createStatement(requireNotNull(dialect.sqlSetSearchPath(path))) as io.trino.sql.tree.Use
        assertEquals("a\"b", parsed.catalog.orElseThrow().value)
        assertEquals("c.d", parsed.schema.value)
        assertNull(dialect.sqlSetSearchPath(SearchPath.of(ObjectPath.create("tiny", ObjectKind.SCHEMA))))
        assertNull(dialect.sqlSetSearchPath(SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE))))
    }
}

package dev.sort.trino

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.assertIntrospectorRoutesToGeneric
import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.util.Version

class TrinoIntrospectorRoutingTest : BasePlatformTestCase() {
    fun testIntrospectionRoutesToGenericJdbc() {
        assertIntrospectorRoutesToGeneric(TrinoDbms.TRINO_BRIKK)
    }

    private val factoryClass get() = Class.forName("com.intellij.database.introspection.DBIntrospectorFactory",
        true, Class.forName("com.intellij.database.dialects.postgres.model.PgMetaModel").classLoader)

    private fun forDbms(dbms: Dbms): Any {
        val ep = factoryClass.getMethod("getINTRO_EP").invoke(null)
        return requireNotNull(ep.javaClass.getMethod("forDbms", Dbms::class.java).invoke(ep, dbms))
    }

    private fun nativeSupported(dbms: Dbms, major: Int) = factoryClass
        .getMethod("supportedByNativeIntrospector", Dbms::class.java, Version::class.java)
        .invoke(null, dbms, Version.of(major)) as Boolean

    fun testGateShadowsPgFallbackOnTheIntrospectorEp() {
        val factory = forDbms(TrinoDbms.TRINO_BRIKK)
        assertInstanceOf(factory, TrinoIntrospectorGate::class.java)
        assertEquals(true, factory.javaClass.getMethod("isNative").invoke(factory))
    }

    fun testNativeSelectionVetoedAtEveryServerVersion() {
        for (major in listOf(483, 476, 9, 8, 1)) {
            assertFalse(TrinoIntrospectorGate().isSupported(Version.of(major)))
            assertFalse("native introspector at $major", nativeSupported(TrinoDbms.TRINO_BRIKK, major))
        }
    }

    fun testFreshTrinoBrikkDataSourceSelectsGenericByFormula() {
        val source = LocalDataSource.create("routing-parity", "io.trino.jdbc.TrinoDriver", "jdbc:trino://localhost:8080", "")
        source.databaseDriver = requireNotNull(DatabaseDriverManager.getInstance().getDriver("trino-brikk"))
        assertEquals(TrinoDbms.TRINO_BRIKK, source.dbms)
        assertFalse(source.useJdbcIntrospector())
        for (flag in listOf(false, true, false)) {
            source.setUseJdbcIntrospector(flag)
            assertTrue(source.useJdbcIntrospector() || !nativeSupported(source.dbms, 483))
        }
    }

    fun testPostgresNativeIntrospectorUntouched() {
        val factory = forDbms(Dbms.POSTGRES)
        assertTrue(factory.javaClass.name.contains("PgIntrospector"))
        assertEquals(true, factory.javaClass.getMethod("isNative").invoke(factory))
        assertTrue(nativeSupported(Dbms.POSTGRES, 17))
    }

    fun testGateCreateIntrospectorIsUnreachableAndLoud() {
        val context = object : com.intellij.database.introspection.DBIntrospectionContext {
            override val project: com.intellij.openapi.project.Project get() = error("must not inspect the context")
            override fun getUserNotifier(title: String) = error("must not notify")
        }
        try {
            TrinoIntrospectorGate().createIntrospector(context, TrinoDbms.TRINO_BRIKK,
                com.intellij.database.model.ModelFactory.BLACK_HOLE)
            fail("native creation must be unreachable")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("generic JDBC introspector"))
        }
    }
}

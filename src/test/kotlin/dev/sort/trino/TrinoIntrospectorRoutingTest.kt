package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.util.Version
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * THE LANDMINE FIX, verified against the real platform classes (the post-fix inversion of the
 * truth battery's `TrinoIntrospectorGateTest`, which pinned the pre-fix failure: TRINO_BRIKK at
 * server major 483 selected the NATIVE PG introspector via our `extensionFallback → POSTGRES`,
 * and PG's first `pg_catalog` query cannot run on Trino).
 *
 * Selection formula (disassembled from DatabaseTools 2026.1.3, re-proven by the truth lane):
 * `generic = dataSource.useJdbcIntrospector() || !supportedByNativeIntrospector(dbms, version)`
 * where `supportedByNativeIntrospector == INTRO_EP.forDbms(dbms).isNative() && .isSupported(version)`.
 * With [TrinoIntrospectorGate] registered dbms-exact, `forDbms(TRINO_BRIKK)` is OUR factory,
 * `isSupported` is false for every version, so the formula picks GENERIC for every TRINO_BRIKK
 * data source — independent of the per-source flag, data-source age, or IDE updates.
 *
 * Reflection is used for the selection entry points because they are Kotlin-`internal` in
 * metadata (public in bytecode) — same technique and reason as [PgModelAccess] and the truth
 * probe.
 */
class TrinoIntrospectorRoutingTest : BasePlatformTestCase() {

    /** Any class from the postgres dialect module fixes the classloader that sees the platform. */
    private val pgLoader: ClassLoader
        get() = Class.forName("com.intellij.database.dialects.postgres.model.PgMetaModel").classLoader

    private fun dbIntrospectorFactory() =
        Class.forName("com.intellij.database.introspection.DBIntrospectorFactory", true, pgLoader)

    private fun supportedByNativeIntrospector(dbms: Dbms, version: Version): Boolean =
        dbIntrospectorFactory()
            .getMethod("supportedByNativeIntrospector", Dbms::class.java, Version::class.java)
            .invoke(null, dbms, version) as Boolean

    private fun forDbms(dbms: Dbms): Any? {
        val dif = dbIntrospectorFactory()
        val ep = dif.getMethod("getINTRO_EP").invoke(null)
        return ep.javaClass.getMethod("forDbms", Dbms::class.java).invoke(ep, dbms)
    }

    private fun isNative(factory: Any): Boolean =
        factory.javaClass.getMethod("isNative").invoke(factory) as Boolean

    /** The routing itself: dbms-exact registration SHADOWS the POSTGRES fallback on this EP. */
    fun testGateShadowsPgFallbackOnTheIntrospectorEp() {
        val factory = forDbms(TrinoDbms.TRINO_BRIKK)
        assertNotNull("an introspector factory must resolve for TRINO_BRIKK", factory)
        assertTrue(
            "forDbms(TRINO_BRIKK) must resolve to TrinoIntrospectorGate (not the PG fallback), " +
                "got ${factory!!.javaClass.name}",
            factory is TrinoIntrospectorGate,
        )
        assertTrue("the gate must sit in the native slot for the veto to bind", isNative(factory))
    }

    /** The veto holds at EVERY server version — 483 (real Trino), the PG boundary, and below. */
    fun testNativeSelectionVetoedAtEveryServerVersion() {
        val gate = TrinoIntrospectorGate()
        for (major in intArrayOf(483, 476, 9, 8, 1)) {
            assertFalse("gate must support no version, claimed $major", gate.isSupported(Version.of(major)))
            assertFalse(
                "LANDMINE must stay defused: supportedByNativeIntrospector(TRINO_BRIKK, $major) " +
                    "must be false so the platform selects the generic JDBC introspector",
                supportedByNativeIntrospector(TrinoDbms.TRINO_BRIKK, Version.of(major)),
            )
        }
    }

    /**
     * The production formula for a REAL data source wearing our driver (forced-dbms TRINO_BRIKK
     * from driversConfig): generic is selected with the per-source flag at its default (false),
     * and the flag no longer matters in either position — so a user who ticks or unticks
     * "Use JDBC-based introspector" in Options is respected AND safe. The plugin never writes
     * the flag (this is why Path B was rejected: the platform force-resets it as
     * `legacy-introspector` after every IDE build change).
     */
    fun testFreshTrinoBrikkDataSourceSelectsGenericByFormula() {
        val driver = DatabaseDriverManager.getInstance().getDriver("trino-brikk")
        assertNotNull("driversConfig must register trino-brikk", driver)
        val dataSource = LocalDataSource.create(
            "routing-probe", "io.trino.jdbc.TrinoDriver", "jdbc:trino://localhost:8080", "",
        )
        dataSource.databaseDriver = driver
        assertEquals(TrinoDbms.TRINO_BRIKK, dataSource.dbms)
        assertFalse("creation must NOT touch the per-source flag", dataSource.useJdbcIntrospector())

        val v483 = Version.of(483) // what trino-jdbc reports, pinned by TrinoDriverFactsTest
        fun genericSelected() =
            dataSource.useJdbcIntrospector() || !supportedByNativeIntrospector(dataSource.dbms, v483)

        assertTrue("flag=false (default): generic via the gate's veto", genericSelected())
        dataSource.setUseJdbcIntrospector(true)
        assertTrue("flag=true (user ticked): generic via the flag", genericSelected())
        dataSource.setUseJdbcIntrospector(false)
        assertTrue("flag=false (user unticked): STILL generic via the gate — untick stays safe", genericSelected())
    }

    /** dbms-exact scope: the POSTGRES family we fall back to for everything else is untouched. */
    fun testPostgresNativeIntrospectorUntouched() {
        val pg = forDbms(Dbms.POSTGRES)
        assertNotNull(pg)
        assertTrue(
            "POSTGRES must keep its own native introspector, got ${pg!!.javaClass.name}",
            pg.javaClass.name.contains("PgIntrospector"),
        )
        assertTrue(isNative(pg))
        assertTrue(
            "PG's own version gate must keep working for real Postgres servers",
            supportedByNativeIntrospector(Dbms.POSTGRES, Version.of(17)),
        )
    }

    /** The gate's create is unreachable by the selection contract — and loud if that ever changes. */
    fun testGateCreateIntrospectorIsUnreachableAndLoud() {
        val gate = TrinoIntrospectorGate()
        // Inert arguments: the gate throws before touching either (the context's members prove it).
        val context = object : com.intellij.database.introspection.DBIntrospectionContext {
            override val project: com.intellij.openapi.project.Project
                get() = throw AssertionError("gate must not consult the context")
            override fun getUserNotifier(title: String) = throw AssertionError("gate must not notify")
        }
        try {
            gate.createIntrospector(context, TrinoDbms.TRINO_BRIKK, com.intellij.database.model.ModelFactory.BLACK_HOLE)
            fail("createIntrospector must never produce an introspector")
        } catch (e: IllegalStateException) {
            assertTrue(
                "the failure must point at the selection contract, got: ${e.message}",
                e.message!!.contains("generic JDBC introspector"),
            )
        }
    }
}

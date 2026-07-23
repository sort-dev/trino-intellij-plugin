package dev.sort.trino.probe

import com.intellij.database.Dbms
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.trino.TrinoDbms

/**
 * INTROSPECTOR DETERMINATION — the OFFLINE (bytecode-mechanism) half of the seed's #1 landmine.
 *
 * Two independent facts decide what introspects a TRINO_BRIKK data source. This test pins the
 * STATIC one, executed against the real DataGrip platform classes (no live server); the LIVE half
 * (server reports major 483, and Trino has no pg_catalog) is pinned by [TrinoPgCatalogLandmineTest].
 * Together they prove the landmine from both ends. Reflection is used throughout because the PG
 * introspector factory + the selection entry point are Kotlin-`internal` in metadata (public in
 * bytecode) — the same reason [dev.sort.trino.PgModelAccess] exists as a Java shim.
 *
 * THE MECHANISM (disassembled from DatabaseTools 2026.1.3, re-proven here as live assertions):
 *   - `DBIntrospectorFactory.supportedByNativeIntrospector(dbms, version)`
 *        == `INTRO_EP.forDbms(dbms).isNative() && .isSupported(version)`
 *   - `INTRO_EP` is fallback-aware; `forDbms(TRINO_BRIKK)` walks our `extensionFallback -> POSTGRES`
 *     to `PgIntrospector.Factory` (isNative()=true by interface default).
 *   - `PgIntrospector.Factory.isSupported(v)` compiles to `v.isOrGreater(9)`.
 *   - Production `createIntrospector(ctx, mf, dataSource)` uses the GENERIC JdbcIntrospector
 *     (`forDbms(Dbms.UNKNOWN)`) iff `dataSource.useJdbcIntrospector() || !supportedByNativeIntrospector`.
 *
 * The DuckDB sibling was SAVED by this gate (DuckDB reports major 1 < 9 -> native rejected -> generic
 * JDBC introspector runs). Trino reports major 483 >= 9, so the gate PASSES and the PG-native
 * introspector is SELECTED for TRINO_BRIKK unless something forces the generic path. That is the
 * landmine — pinned below and analysed in REPORT-truth-tree.md.
 */
class TrinoIntrospectorGateTest : BasePlatformTestCase() {

    // Any class from the postgres dialect module fixes the classloader that can see PgIntrospector.
    private val pgLoader: ClassLoader
        get() = Class.forName("com.intellij.database.dialects.postgres.model.PgMetaModel").classLoader

    private fun versionClass() = Class.forName("com.intellij.database.util.Version", true, pgLoader)

    /** `Version.of(int...)` — the public factory the platform itself uses. */
    private fun version(vararg parts: Int): Any =
        versionClass().getMethod("of", IntArray::class.java).invoke(null, parts)

    private fun pgIntrospectorFactory(): Any =
        Class.forName("com.intellij.database.dialects.postgres.introspector.PgIntrospector\$Factory", true, pgLoader)
            .getDeclaredConstructor().newInstance()

    private fun isNative(factory: Any): Boolean =
        factory.javaClass.getMethod("isNative").invoke(factory) as Boolean

    private fun isSupported(factory: Any, version: Any): Boolean =
        factory.javaClass.getMethod("isSupported", versionClass()).invoke(factory, version) as Boolean

    private fun dbIntrospectorFactory() =
        Class.forName("com.intellij.database.introspection.DBIntrospectorFactory", true, pgLoader)

    private fun supportedByNativeIntrospector(dbms: Dbms, version: Any): Boolean =
        dbIntrospectorFactory()
            .getMethod("supportedByNativeIntrospector", Dbms::class.java, versionClass())
            .invoke(null, dbms, version) as Boolean

    private fun forDbms(dbms: Dbms): Any? {
        val dif = dbIntrospectorFactory()
        val ep = dif.getMethod("getINTRO_EP").invoke(null)
        return ep.javaClass.getMethod("forDbms", Dbms::class.java).invoke(ep, dbms)
    }

    /** The gate itself: `PgIntrospector.Factory.isSupported == version.isOrGreater(9)`, and it is
     *  native. 483 clears it; 8 does not; 9 is the inclusive boundary. */
    fun testPgIntrospectorNativeGateIsMajorVersion9() {
        val factory = pgIntrospectorFactory()
        assertTrue("PG introspector factory must be native (interface default isNative())", isNative(factory))
        assertTrue("Trino major 483 must clear the PG gate (>= 9)", isSupported(factory, version(483)))
        assertTrue("major 9 is the inclusive boundary", isSupported(factory, version(9)))
        assertFalse("major 8 must NOT clear the gate", isSupported(factory, version(8)))
        assertFalse("major 1 (the DuckDB case) must NOT clear the gate", isSupported(factory, version(1)))
    }

    /** THE LANDMINE, composed against the platform's real selection method for OUR dbms: at a Trino
     *  server version (483) the native PG introspector IS selected for TRINO_BRIKK; below 9 it is not.
     *  This is what flips vs DuckDB. */
    fun testTrinoBrikkSelectsNativePgIntrospectorAtServerVersion() {
        val at483 = supportedByNativeIntrospector(TrinoDbms.TRINO_BRIKK, version(483))
        val at8 = supportedByNativeIntrospector(TrinoDbms.TRINO_BRIKK, version(8))
        println("=== supportedByNativeIntrospector(TRINO_BRIKK) === v483=$at483 v8=$at8")
        assertTrue(
            "LANDMINE: at Trino major 483 the platform selects the NATIVE (PG) introspector for " +
                "TRINO_BRIKK — it will then run pg_catalog queries Trino cannot answer (see " +
                "TrinoPgCatalogLandmineTest + REPORT-truth-tree.md)",
            at483,
        )
        assertFalse("below the gate the native introspector must not be selected", at8)
    }

    /** Proves the routing: `forDbms(TRINO_BRIKK)` resolves — via our `extensionFallback -> POSTGRES` —
     *  to the native PgIntrospector factory (not the generic JDBC one). */
    fun testForDbmsResolvesTrinoBrikkToPgIntrospector() {
        val factory = forDbms(TrinoDbms.TRINO_BRIKK)
        assertNotNull("no introspector factory resolved for TRINO_BRIKK", factory)
        val cls = factory!!.javaClass.name
        println("=== forDbms(TRINO_BRIKK) === $cls native=${isNative(factory)}")
        assertTrue("TRINO_BRIKK must resolve (via POSTGRES fallback) to the PG introspector, got $cls", cls.contains("PgIntrospector"))
        assertTrue("the resolved PG introspector is native", isNative(factory))
    }
}

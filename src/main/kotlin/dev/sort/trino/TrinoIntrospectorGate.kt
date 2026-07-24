package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.database.introspection.DBIntrospectionContext
import com.intellij.database.introspection.DBIntrospector
import com.intellij.database.model.ModelFactory
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.basic.BasicElement
import com.intellij.database.util.Version

/**
 * THE LANDMINE FIX (truth battery, REPORT-truth-tree.md): route every TRINO_BRIKK data source to
 * the platform's GENERIC JDBC introspector by *vetoing* native-introspector selection.
 *
 * ## Why this exists
 *
 * The platform picks an introspector via
 * `supportedByNativeIntrospector(dbms, version) == INTRO_EP.forDbms(dbms).isNative() && .isSupported(version)`;
 * only when that is false (or the per-source "Use JDBC-based introspector" flag is set) does the
 * generic `JdbcIntrospector` run. Without this class, `forDbms(TRINO_BRIKK)` walks our
 * `extensionFallback → POSTGRES` to `PgIntrospector.Factory`, whose gate is `version.isOrGreater(9)`
 * — and trino-jdbc reports **major 483**. The PG-native introspector is selected and its first
 * `pg_catalog` query throws at attach: Trino has no `pg_catalog` at all.
 *
 * This factory registers dbms-exact (`<introspector dbms="TRINO_BRIKK">`), which fully shadows the
 * POSTGRES fallback for this EP (`DbmsExtension.buildExtensions` copies from fallback only when the
 * exact key has no registrations). It stays native-slot (`isNative() = true`, the interface
 * default) but supports **no version** — so the selection formula yields false for every server
 * version and the generic JDBC introspector runs, reading the strong metadata surface the truth
 * battery measured (catalogs = connectors, schemas, tables/views, columns with full Trino type
 * spellings, NOT NULL).
 *
 * ## Why not the public per-source flag (Path B, rejected on bytecode evidence)
 *
 * `LocalDataSource.setUseJdbcIntrospector(true)` is public API and there IS a clean public
 * creation seam (`DataSourceManager.TOPIC` → `dataSourceAdded`). But the platform treats that flag
 * as a *transient workaround by design*: it serializes as `legacy-introspector`, and
 * `DataSourceStorage.readStateImpl` force-resets it to false (with an "introspection using JDBC
 * metadata disabled" notification) whenever the stored `created-in` build differs from the running
 * IDE build — i.e. after **every IDE update** the landmine would re-arm on every data source, and
 * silently re-forcing the flag on load would trample users who deliberately unticked it (a
 * platform-reset and a user-untick are indistinguishable: both leave the flag false). Pre-existing
 * data sources would never be covered either. This gate is selection-time and per-dbms, so it is
 * immune to all of that; the checkbox keeps working but no longer matters (generic either way).
 *
 * ## Internal-API defense (doris/duckdb precedent, IJPL-249765)
 *
 * `DBIntrospector.Factory` and `DBIntrospector` are `@ApiStatus.Internal`. Usage here is
 * **unavoidable**: the introspector-selection seam has no public surface (the only public lever is
 * the per-source flag rejected above), and the doris plugin already ships a factory on this exact
 * EP (`<introspector dbms="DORIS">`, defended under IJPL-249765 — JetBrains ticket asking for a
 * public seam). Exposure is **pinned**: this class implements the interface's four abstract
 * members and overrides nothing else, instantiates no platform introspector, and its
 * `createIntrospector` is unreachable (see below). It is **verified-on-build**: the routing test
 * asserts the selection outcome against the real platform bytecode on every test run, and the
 * Marketplace verifier inventories the usage on every release. 262-compat note (doris
 * COMPAT-262.md): platform 262 deprecates 1-arg `isSupported(Version)` for a new default overload
 * `isSupported(Version, DatabaseConnectionPoint, Project)` that routes into it; on 261 the 1-arg
 * form is abstract, so a single 261+262 artifact must implement it — cost is one deprecation
 * warning on 262, not a compatibility problem.
 */
class TrinoIntrospectorGate : DBIntrospector.Factory {

    /** Native slot for TRINO_BRIKK (interface default, made explicit: the veto below must win). */
    override fun isNative(): Boolean = true

    /** The veto: no server version is "supported natively" → the platform always picks generic. */
    override fun isSupported(version: Version): Boolean = false

    /**
     * Unreachable by construction: both production entry points
     * (`DBIntrospectorFactory.createIntrospector` overloads) resolve `forDbms(Dbms.UNKNOWN)` — the
     * generic factory — whenever `isSupported` is false, so this dbms-keyed factory is never asked
     * to create. Throwing (rather than delegating) keeps the guarantee loud if a future platform
     * changes the selection contract.
     */
    override fun createIntrospector(
        context: DBIntrospectionContext,
        dbms: Dbms,
        modelFactory: ModelFactory,
    ): DBIntrospector = throw IllegalStateException(
        "TrinoIntrospectorGate vetoes native introspection for TRINO_BRIKK (isSupported=false for " +
            "all versions); the platform must select the generic JDBC introspector instead. If this " +
            "is thrown, the introspector-selection contract changed — re-run the truth battery.",
    )

    /** No native source-introspection algorithm exists for the gate; 0 = unversioned. */
    override fun getVersion(kind: ObjectKind): Int = 0

    /** Generic JDBC introspection loads no source text, so outdated-source checks never apply. */
    override fun isOutdatedCheckSupported(e: BasicElement?): Boolean = false

    // Interface defaults deliberately kept: isIncremental() = false,
    // supportsMultilevelIntrospection = false, supportsFragmentIntrospection = false — all
    // truthful for generic JDBC introspection (no increments, no levels, no fragments).
}

package dev.sort.trino.catalog

import com.intellij.database.dataSource.DataSourceSyncManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.basic.BasicElement
import com.intellij.database.util.LoaderContext
import com.intellij.database.util.TreePattern
import com.intellij.database.util.TreePatternUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.sort.trino.catalog.TrinoNamespacePath.Level
import org.jetbrains.annotations.TestOnly

/**
 * LAZY, TARGETED introspection for Trino (Brikk) — ported from the sibling doris plugin's
 * production `DorisPipesAutoIntrospect`. When the user path-types into an enumerated-but-childless
 * namespace (`hive.` whose schemas were never loaded; `hive.web.` whose tables were never loaded),
 * don't make them go click "introspect": widen the data source's `introspectionScope` to EXACTLY
 * that node (union never clobbers the user's own selections) and kick a TARGETED one-element
 * refresh. The platform's own completion then shows the freshly-loaded names on the next invoke.
 *
 * ## The scale discipline (the whole point)
 *
 * Trino is massively multi-catalog. Two rules keep this safe:
 *  - **One level at a time.** A catalog deepening loads only that catalog's schemas; a schema
 *    deepening loads only that schema's tables. Enforced by [TrinoIntrospectionTasks.oneElementRefresh]
 *    (doris round 18: a general/scope task re-introspected the ENTIRE widened scope and hung on
 *    slow externals — one element, one refresh, regardless of how wide the scope is).
 *  - **One shot per (data source, namespace) per IDE session.** A failed or empty introspection
 *    never loops — [claimOnce] dedupes on a stable key.
 *
 * ## API-status defense (doris/duckdb precedent, IJPL-249765)
 *
 * The introspection-driving surface is the exact set the doris plugin ships on. Per-symbol status
 * (javap-verified on DataGrip 2026.1.3):
 *  - [LoaderContext] is `@ApiStatus.Internal` — unavoidable: it is the only public factory for a
 *    targeted introspection task (`selectTask`), and there is no non-internal seam. Pinned to the
 *    two factory calls here; the same class the doris plugin already uses under this ticket.
 *  - [DataSourceSyncManager].tryPerform is Kotlin-`@Deprecated` ("use coroutines"). The coroutine
 *    replacement is `tryPerformSync` (a suspend fun); this fail-soft, fire-and-forget completion
 *    path is not a coroutine context, so the stable non-suspend entry point is the correct one.
 *  - `IntrospectionTasks` is Kotlin-`internal` in metadata (public bytecode), reached through the
 *    [TrinoIntrospectionTasks] Java shim (Java ignores Kotlin visibility) — same as doris.
 *  - [TreePattern]/[TreePatternUtils]/`TreePatternNode`/`ObjectName`/`ObjectKind` and
 *    [LocalDataSource.setIntrospectionScope] carry NO ApiStatus flags.
 */
object TrinoAutoIntrospect {

    private val LOG = Logger.getInstance(TrinoAutoIntrospect::class.java)

    /** One-shot guard: `uniqueId|LEVEL:catalog[.schema]` already requested this IDE session. */
    private val requested = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Stable dedupe key for a deepening (factored for direct unit testing). */
    internal fun keyFor(uniqueId: String, level: Level, catalog: String, schema: String?): String =
        when (level) {
            Level.CATALOG -> "$uniqueId|CATALOG:$catalog"
            Level.SCHEMA -> "$uniqueId|SCHEMA:$catalog.$schema"
        }

    /** True the FIRST time [key] is seen; false forever after (per IDE session). Unit-testable. */
    internal fun claimOnce(key: String): Boolean = requested.add(key)

    /** The scope addition for a deepening (pure — delegates to [TrinoCatalogScopes]). */
    internal fun scopeFor(level: Level, catalog: String, schema: String?): TreePattern = when (level) {
        Level.CATALOG -> TrinoCatalogScopes.catalogSchemasScope(catalog)
        Level.SCHEMA -> TrinoCatalogScopes.schemaTablesScope(catalog, schema!!)
    }

    /**
     * Kick a targeted introspection deepening [catalog] (→ schemas) or [catalog].[schema]
     * (→ tables) on [local]. Returns true iff a NEW introspection was started; false when it was
     * already requested this session or anything went wrong (always fail-soft — a completion pass
     * must never throw or block).
     *
     * [node] must be the resolved model element (a [BasicElement]) for the one-element refresh; if
     * it is not (defensive — the model always hands out BasicElements), the scope is still widened
     * so a later manual refresh honours it, but no sync is kicked.
     */
    fun request(
        project: Project,
        local: LocalDataSource,
        level: Level,
        catalog: String,
        schema: String?,
        node: Any?,
    ): Boolean {
        val key = keyFor(local.uniqueId, level, catalog, schema)
        if (!claimOnce(key)) return false
        return runCatching {
            val addition = scopeFor(level, catalog, schema)
            val current = local.introspectionScope
            local.introspectionScope =
                if (current == null) addition else TreePatternUtils.union(current, addition)

            val element = node as? BasicElement
            if (element == null) {
                LOG.info("auto-introspect: scope widened for $key; node is not a BasicElement — no refresh kicked")
                return@runCatching false
            }
            val task = TrinoIntrospectionTasks.oneElementRefresh(local.uniqueId, element)
            // API status: LoaderContext is @ApiStatus.Internal, tryPerform is @Deprecated
            // (coroutines) — see the class KDoc; both are the only stable seam for a targeted
            // introspection from a non-suspend, fail-soft path (doris precedent, IJPL-249765).
            DataSourceSyncManager.getInstance()
                .tryPerform(LoaderContext.selectTask(project, local, task), true, false)
            LOG.info("auto-introspect: scope widened + TARGETED one-element refresh for $key")
            true
        }.getOrElse { t ->
            LOG.warn("auto-introspect failed for $key: ${t.message}", t)
            false
        }
    }

    /** Test hook: forget every one-shot claim so a case can re-request in a fresh scenario. */
    @TestOnly
    fun resetForTest() {
        requested.clear()
    }
}

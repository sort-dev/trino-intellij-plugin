package dev.sort.trino.catalog

import com.intellij.database.model.ObjectKind
import com.intellij.database.model.ObjectName
import com.intellij.database.util.TreePattern
import com.intellij.database.util.TreePatternNode

/**
 * Introspection-scope vocabulary for Trino (Brikk) data sources — the TreePattern building blocks
 * the lazy/targeted introspection ([TrinoAutoIntrospect]) unions into a data source's
 * `introspectionScope`. Ported from the sibling doris plugin's `DorisCatalogScopes` /
 * `DorisPipesAutoIntrospect`; the pattern vocabulary (public API) is identical because Trino sits
 * on the same PG family model: **catalog = [ObjectKind.DATABASE], schema = [ObjectKind.SCHEMA],
 * table = [ObjectKind.TABLE]** (truth battery: the generic JDBC introspector surfaces every Trino
 * connector as a catalog via `getCatalogs()`).
 *
 * ## The scale discipline (why these shapes)
 *
 * Trino is massively multi-catalog: a `hive`/`iceberg` catalog can front thousands of schemas and
 * tables. Live-measured rule (TrinoAutoIntrospectLiveTest): **a SELECTED scope node causes its
 * DIRECT CHILDREN to load, one level, not its whole subtree** — a leaf `DATABASE(cat)` loads that
 * catalog's schemas but no tables; a `DATABASE(cat) → SCHEMA(sch)` loads that schema's tables. So
 * a catalog deepen must be a bare `DATABASE(cat)` leaf (NOT a `SCHEMA(*)` group, which would select
 * every schema and pull all their tables). These scopes are only ever unioned in ONE node at a
 * time, and the actual loading is further bounded by a TARGETED one-element refresh
 * ([TrinoIntrospectionTasks.oneElementRefresh], doris round-18 lesson), never a scope-wide sync.
 * The scope union is for PERSISTENCE (the freshly-enumerated nodes stay in scope and don't get
 * pruned); the one-element refresh is what keeps each deepening to a single level.
 *
 * ## No connect interceptor (measured)
 *
 * The task considered a connect-time interceptor to enumerate the catalog top level. Live
 * measurement (TrinoAutoIntrospectLiveTest, STAGE1) showed it is UNNECESSARY: a fresh Trino data
 * source's default `@:@` scope already enumerates every catalog on the first sync (Trino reports no
 * current catalog, so `@` matches nothing deeper — catalogs list, schemas/tables stay lazy). So the
 * plugin ships no interceptor; these scopes serve only the completion-time DEEPENING below.
 *
 * Pattern vocabulary (all public API): [TreePatternNode.PositiveNaming] (named match),
 * [TreePatternNode.Group] (one [ObjectKind] level), [TreePatternNode.NO_GROUPS] (a leaf — no
 * deeper level selected).
 */
object TrinoCatalogScopes {

    /** A leaf pattern node matching exactly [name] at its level. */
    private fun namedLeaf(name: String): TreePatternNode =
        TreePatternNode(TreePatternNode.PositiveNaming(ObjectName.plain(name)), TreePatternNode.NO_GROUPS)

    /**
     * **Catalog deepening (`hive.` → its schemas):** select exactly [catalog] as a DATABASE leaf so
     * a targeted refresh of the catalog node loads its schemas (its direct children) and they
     * persist — `DATABASE(catalog)`, NO schema group.
     *
     * The bare catalog leaf is deliberate: a `SCHEMA(*)` group here would *select the schemas*, and
     * a selected schema pulls ALL its tables (live rule) — catastrophic across a many-thousand-table
     * hive catalog. Tables load only when the user later path-types into a specific schema (see
     * [schemaTablesScope]).
     */
    fun catalogSchemasScope(catalog: String): TreePattern =
        TreePattern(TreePatternNode.Group(ObjectKind.DATABASE, arrayOf(namedLeaf(catalog))))

    /**
     * **Schema deepening (`hive.web.` → its tables):** select exactly [catalog].[schema] so a
     * targeted refresh of the schema node loads its tables/columns. This is the sibling doris
     * plugin's production-proven auto-introspect pattern:
     * `DATABASE(catalog) → SCHEMA(schema)` (leaf schema).
     *
     * [catalog] may be null when the completion context resolved a schema under the console's
     * current catalog without an explicit catalog segment — then the SCHEMA group is rooted
     * directly (`SCHEMA(schema)`), matching doris.
     */
    fun schemaTablesScope(catalog: String?, schema: String): TreePattern {
        val schemaLeaf = namedLeaf(schema)
        return if (catalog != null) {
            TreePattern(
                TreePatternNode.Group(
                    ObjectKind.DATABASE,
                    arrayOf(
                        TreePatternNode(
                            TreePatternNode.PositiveNaming(ObjectName.plain(catalog)),
                            arrayOf(TreePatternNode.Group(ObjectKind.SCHEMA, arrayOf(schemaLeaf))),
                        ),
                    ),
                ),
            )
        } else {
            TreePattern(TreePatternNode.Group(ObjectKind.SCHEMA, arrayOf(schemaLeaf)))
        }
    }
}

package dev.sort.trino.catalog

import com.intellij.database.model.ObjectKind
import com.intellij.database.model.ObjectName
import com.intellij.database.util.Casing
import com.intellij.database.util.TreePattern
import com.intellij.database.util.TreePatternNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * OFFLINE assertions for the introspection-scope patterns the lazy deepening unions into a data
 * source (ported from the sibling doris plugin's `DorisCatalogScopesTest`). What a live Trino must
 * still confirm — that the platform actually loads exactly one level per targeted refresh — is
 * pinned by `TrinoAutoIntrospectLiveTest`; the pattern CONTENT is fully assertable here.
 *
 * Model levels (PG family, verified by the truth battery): catalog = [ObjectKind.DATABASE],
 * schema = [ObjectKind.SCHEMA].
 */
class TrinoCatalogScopesTest : BasePlatformTestCase() {

    private fun dbGroup(p: TreePattern): TreePatternNode.Group? = p.root?.getGroup(ObjectKind.DATABASE)

    private fun childMatching(group: TreePatternNode.Group?, name: String): TreePatternNode? =
        group?.children.orEmpty().firstOrNull { it.naming.matches(ObjectName.plain(name), Casing.EXACT) }

    fun testCatalogSchemasScopeIsABareCatalogLeaf() {
        // Live rule (STAGE2): a selected DATABASE leaf loads that catalog's SCHEMAS (its direct
        // children) and NO tables. A SCHEMA(*) group here would instead select the schemas and pull
        // every one of their tables — catastrophic on a many-thousand-table hive catalog.
        val p = TrinoCatalogScopes.catalogSchemasScope("hive")
        val hive = childMatching(dbGroup(p), "hive")
        assertNotNull("catalog deepening must name the catalog 'hive'", hive)
        assertFalse("a different catalog must NOT match", dbGroup(p)!!.children.orEmpty().any {
            it.naming.matches(ObjectName.plain("iceberg"), Casing.EXACT)
        })
        assertNull(
            "catalog deepening must be a BARE DATABASE leaf (no SCHEMA group) so it never cascades to tables",
            hive!!.getGroup(ObjectKind.SCHEMA),
        )
    }

    fun testSchemaTablesScopeSelectsExactlyOneSchemaUnderCatalog() {
        val p = TrinoCatalogScopes.schemaTablesScope("hive", "web")
        val hive = childMatching(dbGroup(p), "hive")
        assertNotNull("schema deepening must name the catalog 'hive'", hive)
        val schemaGroup = hive!!.getGroup(ObjectKind.SCHEMA)
        assertNotNull("must reach the SCHEMA level", schemaGroup)
        val web = schemaGroup!!.children.orEmpty().firstOrNull {
            it.naming.matches(ObjectName.plain("web"), Casing.EXACT)
        }
        assertNotNull("must name exactly the schema 'web'", web)
        assertFalse(
            "must NOT select any other schema (targeted, not the whole catalog)",
            schemaGroup.children.orEmpty().any { it.naming.matches(ObjectName.plain("other"), Casing.EXACT) },
        )
    }

    fun testSchemaTablesScopeWithoutCatalogRootsSchemaGroupDirectly() {
        val p = TrinoCatalogScopes.schemaTablesScope(null, "web")
        assertNull("no catalog was given -> no DATABASE group", dbGroup(p))
        val schemaGroup = p.root?.getGroup(ObjectKind.SCHEMA)
        assertNotNull("the SCHEMA group must be rooted directly", schemaGroup)
        assertTrue(
            "and it names 'web'",
            schemaGroup!!.children.orEmpty().any { it.naming.matches(ObjectName.plain("web"), Casing.EXACT) },
        )
    }
}

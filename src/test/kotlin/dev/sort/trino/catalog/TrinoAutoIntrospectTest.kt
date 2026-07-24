package dev.sort.trino.catalog

import com.intellij.database.model.ObjectKind
import com.intellij.database.model.ObjectName
import com.intellij.database.util.Casing
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.trino.catalog.TrinoNamespacePath.Level

/**
 * OFFLINE assertions for the request orchestration's testable parts: the one-shot dedupe guard
 * ([TrinoAutoIntrospect.claimOnce] + [TrinoAutoIntrospect.keyFor]) and the level→scope dispatch
 * ([TrinoAutoIntrospect.scopeFor]). The side-effectful `request(...)` (scope union + platform
 * refresh) is exercised live by `TrinoAutoIntrospectLiveTest`.
 */
class TrinoAutoIntrospectTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        TrinoAutoIntrospect.resetForTest()
    }

    override fun tearDown() {
        try {
            TrinoAutoIntrospect.resetForTest()
        } finally {
            super.tearDown()
        }
    }

    fun testKeyDistinguishesLevelsCatalogsAndSchemas() {
        val ds = "ds-1"
        val catHive = TrinoAutoIntrospect.keyFor(ds, Level.CATALOG, "hive", null)
        val catIce = TrinoAutoIntrospect.keyFor(ds, Level.CATALOG, "iceberg", null)
        val schHiveWeb = TrinoAutoIntrospect.keyFor(ds, Level.SCHEMA, "hive", "web")
        val schHiveApp = TrinoAutoIntrospect.keyFor(ds, Level.SCHEMA, "hive", "app")

        assertFalse("different catalogs -> different keys", catHive == catIce)
        assertFalse("different schemas under the same catalog -> different keys", schHiveWeb == schHiveApp)
        assertFalse("a catalog deepen and a schema deepen are never the same key", catHive == schHiveWeb)
        // Data source id is part of the key: the same namespace on two data sources is independent.
        assertFalse(catHive == TrinoAutoIntrospect.keyFor("ds-2", Level.CATALOG, "hive", null))
    }

    fun testClaimOnceIsTrueOnceThenFalse() {
        val k = "ds-1|CATALOG:hive"
        assertTrue("first claim must succeed", TrinoAutoIntrospect.claimOnce(k))
        assertFalse("second claim of the same key must fail (one shot per session)", TrinoAutoIntrospect.claimOnce(k))
        assertTrue("a different key is independent", TrinoAutoIntrospect.claimOnce("ds-1|SCHEMA:hive.web"))
        // The reset hook clears the guard (so a fresh scenario can request again).
        TrinoAutoIntrospect.resetForTest()
        assertTrue("after reset the key is claimable again", TrinoAutoIntrospect.claimOnce(k))
    }

    fun testScopeForCatalogLevelIsABareCatalogLeaf() {
        val p = TrinoAutoIntrospect.scopeFor(Level.CATALOG, "hive", null)
        val hive = p.root?.getGroup(ObjectKind.DATABASE)?.children.orEmpty()
            .firstOrNull { it.naming.matches(ObjectName.plain("hive"), Casing.EXACT) }
        assertNotNull("CATALOG level must produce a catalog-leaf scope naming 'hive'", hive)
        // Bare leaf (no SCHEMA group): loads hive's schemas, never cascades to tables (live rule).
        assertNull("catalog deepen must NOT carry a SCHEMA group", hive!!.getGroup(ObjectKind.SCHEMA))
    }

    fun testScopeForSchemaLevelIsTheSchemaTablesScope() {
        val p = TrinoAutoIntrospect.scopeFor(Level.SCHEMA, "hive", "web")
        val hive = p.root?.getGroup(ObjectKind.DATABASE)?.children.orEmpty()
            .firstOrNull { it.naming.matches(ObjectName.plain("hive"), Casing.EXACT) }
        val web = hive?.getGroup(ObjectKind.SCHEMA)?.children.orEmpty()
            .firstOrNull { it.naming.matches(ObjectName.plain("web"), Casing.EXACT) }
        assertNotNull("SCHEMA level must produce a catalog.schema scope naming exactly hive.web", web)
    }
}

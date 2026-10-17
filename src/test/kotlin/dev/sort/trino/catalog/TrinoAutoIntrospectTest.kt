package dev.sort.trino.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.catalog.AutoIntrospectEngine
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.catalog.CatalogScopes
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.ObjectName
import com.intellij.database.util.Casing

class TrinoAutoIntrospectTest : BasePlatformTestCase() {
    fun testKeyDistinguishesLevelsCatalogsAndSchemas() {
        val errors = BackgroundErrors(testRootDisposable)
        val catalog = AutoIntrospectEngine.Key("ds-1", listOf("hive"))
        val schema = AutoIntrospectEngine.Key("ds-1", listOf("hive", "web"))
        assertFalse(catalog == AutoIntrospectEngine.Key("ds-1", listOf("iceberg")))
        assertFalse(schema == AutoIntrospectEngine.Key("ds-1", listOf("hive", "app")))
        assertFalse(catalog == schema)
        assertFalse(catalog == AutoIntrospectEngine.Key("ds-2", listOf("hive")))
        errors.assertNone()
    }

    fun testClaimOnceIsTrueOnceThenFalse() {
        val engine = AutoIntrospectEngine()
        val key = AutoIntrospectEngine.Key("ds-1", listOf("hive"))
        assertTrue(engine.claim(key))
        assertFalse(engine.claim(key))
        assertTrue(engine.claim(AutoIntrospectEngine.Key("ds-1", listOf("hive", "web"))))
        engine.finish(key, true)
        assertFalse(engine.claim(key))
        assertTrue("a fresh service has a fresh engine", AutoIntrospectEngine().claim(key))
    }

    fun testScopeForCatalogLevelIsABareCatalogLeaf() {
        val scope = CatalogScopes.select(TrinoCatalogProfile.levels, listOf("hive"))
        val hive = scope.root?.getGroup(ObjectKind.DATABASE)?.children.orEmpty()
            .single { it.naming.matches(ObjectName.plain("hive"), Casing.EXACT) }
        assertNull(hive.getGroup(ObjectKind.SCHEMA))
    }

    fun testScopeForSchemaLevelIsTheSchemaTablesScope() {
        val scope = CatalogScopes.select(TrinoCatalogProfile.levels, listOf("hive", "web"))
        val hive = scope.root?.getGroup(ObjectKind.DATABASE)?.children.orEmpty()
            .single { it.naming.matches(ObjectName.plain("hive"), Casing.EXACT) }
        assertNotNull(hive.getGroup(ObjectKind.SCHEMA)?.children.orEmpty()
            .singleOrNull { it.naming.matches(ObjectName.plain("web"), Casing.EXACT) })
    }
}

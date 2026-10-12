package dev.sort.trino.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.catalog.AutoIntrospectEngine
import dev.brikk.house.sql.intellij.testing.BackgroundErrors

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
}

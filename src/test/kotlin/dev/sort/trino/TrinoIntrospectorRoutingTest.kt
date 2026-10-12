package dev.sort.trino

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.assertIntrospectorRoutesToGeneric

class TrinoIntrospectorRoutingTest : BasePlatformTestCase() {
    fun testIntrospectionRoutesToGenericJdbc() {
        assertIntrospectorRoutesToGeneric(TrinoDbms.TRINO_BRIKK)
    }
}

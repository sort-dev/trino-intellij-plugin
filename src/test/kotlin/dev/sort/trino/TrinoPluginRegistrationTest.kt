package dev.sort.trino

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.PluginRegistrationContract

class TrinoPluginRegistrationTest : BasePlatformTestCase() {
    fun testOwnDescriptorExtensionsAndServicesInstantiate() {
        PluginRegistrationContract("dev.sort.trino-intellij-plugin", "dev.sort.trino").check(project)
    }
}

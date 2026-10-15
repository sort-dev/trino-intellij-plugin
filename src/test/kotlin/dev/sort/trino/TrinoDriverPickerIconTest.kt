package dev.sort.trino

import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TrinoDriverPickerIconTest : BasePlatformTestCase() {
    fun testInstalledDriverInheritsForcedDbmsIcon() {
        val driver = requireNotNull(DatabaseDriverManager.getInstance().getDriver("trino-brikk"))
        val dbms = TrinoDbms.TRINO_BRIKK
        assertSame(dbms, driver.forcedDbms)
        assertSame(dbms.icon, driver.getIcon(0))
    }
}

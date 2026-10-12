package dev.sort.trino.catalog

import dev.brikk.house.sql.intellij.testing.JdbcCatalogRows
import java.sql.DriverManager
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrinoLiveCatalogLiveTest {
    @Test fun harvestConnectedServer() {
        val url = System.getProperty("trino.live.url")
        assumeTrue("Live Trino URL not configured", !url.isNullOrBlank())
        DriverManager.getConnection(url).use { connection ->
            val profile = TrinoFunctionCatalogProfile
            val harvest = assertNotNull(profile.harvester.harvest(JdbcCatalogRows(connection, profile.deadline.inWholeSeconds.toInt())))
            assertNotNull(harvest.shared.serverVersion)
            assertTrue(harvest.shared.signatures)
            assertTrue(harvest.shared.functions.any { it.def.name == "abs" })
        }
    }
}

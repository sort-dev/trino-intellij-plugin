package dev.sort.trino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Driver

/**
 * Facts extracted from the REAL io.trino:trino-jdbc jar on the test classpath — keeps
 * config/trino-brikk-drivers.xml honest (driver class, URL scheme) without any server.
 * The quack/duckdb pattern: templates are fact-derived, never guessed.
 */
class TrinoDriverFactsTest {

    @Test
    fun driverClassMatchesDriversXml() {
        val driver = Class.forName("io.trino.jdbc.TrinoDriver").getDeclaredConstructor().newInstance() as Driver
        assertTrue("driver must accept jdbc:trino:// URLs", driver.acceptsURL("jdbc:trino://localhost:8080"))
        assertTrue(driver.acceptsURL("jdbc:trino://example.com:443/hive/web"))
        assertFalse("must not claim foreign schemes", driver.acceptsURL("jdbc:postgresql://localhost/db"))
    }

    @Test
    fun driverVersionMatchesPinnedArtifact() {
        val driver = Class.forName("io.trino.jdbc.TrinoDriver").getDeclaredConstructor().newInstance() as Driver
        // trino-jdbc reports its release train as the major version (483.x).
        assertEquals("pinned artifact and driver major must agree", 483, driver.majorVersion)
    }
}

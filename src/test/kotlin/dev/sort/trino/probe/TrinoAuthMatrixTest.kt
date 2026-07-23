package dev.sort.trino.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.sql.DriverManager
import java.util.Properties

/**
 * DELIVERABLE A — the live authentication matrix that VALIDATES the auth-model comment in
 * config/trino-brikk-drivers.xml. The default (no-TLS) url-template maps the dialog's User field onto
 * `sessionUser=` and MUST keep the Password field empty; the TLS template uses real user/password.
 * Every claim in that comment is a row here, measured against the live server.
 *
 * GATED + OFFLINE-DETERMINISTIC: Assumes out unless `-Dtrino.live.url=...` is set (forwarded by
 * build.gradle.kts). Run live with:
 *   ./gradlew test -Dtrino.live.url='jdbc:trino://localhost:18080?sessionUser=truth'
 * Server recipe: docker run -d --name trino -p 18080:8080 trinodb/trino:483  (no auth)
 */
class TrinoAuthMatrixTest {

    private val liveUrl: String? = System.getProperty("trino.live.url")

    // Base with any query string stripped, so we can append auth variants deterministically.
    private val base: String get() = liveUrl!!.substringBefore("?")

    @Before
    fun gate() {
        assumeTrue("trino.live.url not set — live auth matrix skipped (offline-deterministic)", !liveUrl.isNullOrBlank())
    }

    /** @return the connected `current_user`, or "ERR: <message>" if the connection was refused. */
    private fun currentUserOf(url: String, props: Properties): String =
        try {
            DriverManager.getConnection(url, props).use { c ->
                c.createStatement().use { s -> s.executeQuery("SELECT current_user").use { it.next(); it.getString(1) } }
            }
        } catch (e: Exception) {
            "ERR: ${e.message}"
        }

    private fun props(vararg kv: Pair<String, String>) = Properties().apply { kv.forEach { setProperty(it.first, it.second) } }

    @Test
    fun `1 sessionUser param alone connects and current_user reflects it`() {
        val who = currentUserOf("$base?sessionUser=truth_alice", Properties())
        println("=== [1] sessionUser=truth_alice, no props === current_user=$who")
        assertEquals("sessionUser param must become the query identity", "truth_alice", who)
    }

    @Test
    fun `2 user property alone connects and current_user reflects it`() {
        val who = currentUserOf(base, props("user" to "truth_bob"))
        println("=== [2] user=truth_bob prop === current_user=$who")
        assertEquals("the user property must become the query identity", "truth_bob", who)
    }

    @Test
    fun `3 user property plus sessionUser param — sessionUser wins for current_user`() {
        val who = currentUserOf("$base?sessionUser=truth_carol", props("user" to "truth_bob"))
        println("=== [3] user=truth_bob + sessionUser=truth_carol === current_user=$who")
        // TRUTH: the session-level user (sessionUser) overrides the connection user for the query
        // identity. The `user` prop is still the authenticated principal, but current_user == sessionUser.
        assertEquals("sessionUser must override the user property for current_user", "truth_carol", who)
    }

    @Test
    fun `4 password without SSL is refused — the drivers_xml justification`() {
        val res = currentUserOf(base, props("user" to "truth_dave", "password" to "secret"))
        println("=== [4] password, NO SSL === $res")
        assertTrue("password-without-SSL must be refused; got '$res'", res.startsWith("ERR:"))
        // THE exact error the no-TLS template must avoid by keeping Password empty.
        assertTrue(
            "expected the TLS-required error, got '$res'",
            res.contains("TLS/SSL is required for authentication with username and password"),
        )
    }

    @Test
    fun `5 empty-string user property is rejected even with sessionUser set`() {
        val res = currentUserOf("$base?sessionUser=truth_frank", props("user" to ""))
        println("=== [5] user='' + sessionUser=truth_frank === $res")
        // TRUTH: an empty `user` PROPERTY is validated and rejected before sessionUser is considered.
        // This is why the default template feeds User -> sessionUser param and never sends `user=`:
        // a blank field must simply omit the property (case [1] proves omission works).
        assertTrue("empty user property must be refused; got '$res'", res.startsWith("ERR:"))
        assertTrue("expected the empty-user error, got '$res'", res.contains("Connection property user value is empty"))
    }
}

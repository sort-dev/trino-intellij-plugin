package dev.sort.trino

import com.intellij.database.dataSource.url.template.JdbcTemplateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the shipped driver templates against the "URL only" first-touch bug (0.1.0): the
 * connection dialog showed only a URL field, no host/port. Root cause (confirmed by driving the
 * platform `JdbcTemplateParser` headless): the original path segment used `{catalog}`, which is
 * NOT one of the parser's known location fields — an unknown field in *path/location* position
 * drops the structured editor to URL-only. (Unknown *query* params are fine — cf. Snowflake's
 * `warehouse`/`role`.) The fix uses `{database}` (the canonical path field; the dialog labels it
 * "Database", which for Trino is the catalog) + the `[\?<&,...>]` query-group form.
 *
 * These tests assert over the REAL `config/trino-brikk-drivers.xml`: every template parses without
 * a `processError`, exposes host + port, and uses ONLY canonical location fields (no `catalog`
 * regression). The final structured-vs-URL-only rendering is a Swing decision this headless test
 * can't fully reproduce — the field-name + well-formedness guarantees below are the mechanism.
 */
class TrinoDriverTemplateParseTest {

    private class Collector : JdbcTemplateParser.EventProcessor {
        val params = mutableListOf<String>()
        val errors = mutableListOf<String>()
        override fun processString(s: String) {}
        override fun processGroupStart() {}
        override fun processGroupFinish() {}
        override fun processOptionality() {}
        override fun processNegation() {}
        override fun processParameter(name: String?, p2: String?, p3: String?, p4: String?, p5: String?) {
            if (name != null) params += name
        }
        override fun processError(message: String) { errors += message }
        override fun processListBranch(b: Boolean) {}
        override fun processListStart(s: String) {}
        override fun processListFinish() {}
    }

    private fun templates(): List<Pair<String, String>> {
        val xml = javaClass.getResourceAsStream("/config/trino-brikk-drivers.xml")
            ?: error("trino-brikk-drivers.xml not on the classpath")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val nodes = doc.getElementsByTagName("url-template")
        return (0 until nodes.length).map {
            val e = nodes.item(it) as org.w3c.dom.Element
            e.getAttribute("name") to e.getAttribute("template")
        }.also { assertTrue("expected url-templates in the shipped config", it.isNotEmpty()) }
    }

    @Test
    fun everyTemplateParsesWithoutErrorAndExposesHostPort() {
        for ((name, template) in templates()) {
            val c = Collector()
            JdbcTemplateParser.parse(template, c)
            assertEquals("template '$name' must parse without error (else the dialog falls back to " +
                "URL-only): ${c.errors}", emptyList<String>(), c.errors)
            assertTrue("template '$name' must expose a host field, got ${c.params}", "host" in c.params)
            assertTrue("template '$name' must expose a port field, got ${c.params}", "port" in c.params)
        }
    }

    @Test
    fun defaultTemplateMapsUserOntoSessionUserAndHasNoPasswordField() {
        val (_, template) = templates().first { it.first.startsWith("default") }
        val c = Collector()
        JdbcTemplateParser.parse(template, c)
        // The User dialog field is the source; it is rendered into the sessionUser= URL param.
        assertTrue("default template must carry the User field", "user" in c.params)
        assertTrue("default (no-TLS) template must NOT carry a password field — Trino refuses " +
            "password auth without SSL", "password" !in c.params)
        assertTrue("default template must reference sessionUser in the URL text", template.contains("sessionUser="))
    }

    @Test
    fun sslTemplateForcesSslLiterallyAndDefaults8443() {
        // DataGrip's User&Password auth sends user/password as JDBC PROPERTIES, so a `SSL=true`
        // placed inside the conditional `[\?<&,…>]` group never emits (the group stays empty and
        // the driver rejects the password). SSL=true must be a LITERAL in the URL path, and the
        // port must default to Trino's HTTPS 8443 (not 443).
        val (_, template) = templates().first { it.first == "SSL" }
        assertTrue("SSL template must bake SSL=true as a URL literal, got: $template", template.contains("SSL=true"))
        assertTrue("SSL template must default the port to 8443, got: $template", template.contains("8443"))
        val c = Collector()
        JdbcTemplateParser.parse(template, c)
        assertEquals("SSL template must parse without error: ${c.errors}", emptyList<String>(), c.errors)
        assertTrue("SSL template must still expose host+port, got ${c.params}",
            "host" in c.params && "port" in c.params)
    }

    @Test
    fun templatesAreHostPortOnlyNoPathFields() {
        // Design decision: catalog/schema are NOT in the connection URL — users pick them in code
        // (USE cat.schema) / the Schemas tab. So the templates must carry NO path/location field
        // (the original `{catalog}` bug AND the later `{database}`/`{schema}` both gone) — just
        // host + port + query params. This also sidesteps the whole path-field → "URL only" trap.
        for ((name, template) in templates()) {
            val c = Collector()
            JdbcTemplateParser.parse(template, c)
            for (pathField in listOf("catalog", "database", "schema")) {
                assertTrue("template '$name' must carry no path field (host/port only), found " +
                    "'$pathField' in ${c.params}", pathField !in c.params)
            }
        }
    }
}

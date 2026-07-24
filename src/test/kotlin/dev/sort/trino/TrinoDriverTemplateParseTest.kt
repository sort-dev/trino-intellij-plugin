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
    fun noTemplateUsesTheUnknownCatalogLocationField() {
        // The exact regression: `{catalog}` in path position is not a known location field and
        // dropped the dialog to URL-only. Trino's catalog rides the canonical `{database}` field.
        for ((name, template) in templates()) {
            val c = Collector()
            JdbcTemplateParser.parse(template, c)
            assertTrue("template '$name' must not reuse the unknown '{catalog}' path field " +
                "(use {database}); params=${c.params}", "catalog" !in c.params)
            assertTrue("template '$name' should carry the canonical {database} path field", "database" in c.params)
        }
    }
}

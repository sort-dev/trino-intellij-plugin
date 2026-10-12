package dev.sort.trino.sql

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.metadata.FunctionPresentation
import dev.sort.trino.setSqlDialectMapping

/**
 * Exercises completion in the mapped Trino dialect.
 *
 * Fixture rules (duckdb lessons): map the dialect project-wide (null vfile) BEFORE configureByText —
 * a post-configure mapping leaves the completion session on pre-mapping PSI; and a SINGLE matching
 * variant auto-inserts (the lookup never shows), so multi-hit prefixes are used where the lookup
 * list itself must be inspected.
 */
class TrinoCompletionTest : BasePlatformTestCase() {

    private fun mapAndConfigure(text: String) {
        setSqlDialectMapping(project, null, TrinoSqlDialect.INSTANCE)
        myFixture.configureByText("c.sql", text)
    }

    override fun tearDown() {
        try {
            setSqlDialectMapping(project, null, null)
        } finally {
            super.tearDown()
        }
    }

    fun testScalarFunctionOffered() {
        // date_ prefix has several scalar hits (date_add/date_diff/date_trunc/...) -> lookup shows.
        mapAndConfigure("SELECT date_<caret> FROM t;")
        val variants = myFixture.completeBasic()
        assertNotNull("date_ prefix should present a lookup list", variants)
        val trunc = variants!!.firstOrNull { it.lookupString == "date_trunc" && it.`object` is FunctionPresentation }
        assertNotNull("date_trunc offered; got ${variants.map { it.lookupString }.take(15)}", trunc)
    }

    fun testScalarInsertsParensWithCaretBetween() {
        // `word_stem` is Trino-only (absent from the PG substrate) and prefix-unique, so ONLY our
        // element carries it -> single-match auto-insert runs the parens handler. The defensive
        // branch covers the (unexpected) multi-candidate case by committing our element explicitly.
        mapAndConfigure("SELECT word_stem<caret> FROM t;")
        val variants = myFixture.completeBasic()
        if (variants != null) {
            val ours = variants.firstOrNull { it.lookupString == "word_stem" }
            assertNotNull("word_stem offered; got ${variants.map { it.lookupString }.take(15)}", ours)
            myFixture.lookup.currentItem = ours
            myFixture.type('\n')
        }
        val text = myFixture.editor.document.text
        assertTrue("parens inserted: $text", text.contains("word_stem()"))
        val caret = myFixture.editor.caretModel.offset
        assertEquals("caret sits between the parens", '(', text[caret - 1])
        assertEquals("caret sits between the parens", ')', text[caret])
    }

    fun testAggregateOffered() {
        mapAndConfigure("SELECT approx_<caret> FROM t;")
        val variants = myFixture.completeBasic()
        assertNotNull("approx_ prefix should present a lookup list", variants)
        val agg = variants!!.firstOrNull { it.lookupString == "approx_distinct" && it.`object` is FunctionPresentation }
        assertNotNull("approx_distinct offered; got ${variants.map { it.lookupString }.take(15)}", agg)
    }

    fun testTableFunctionCompletes() {
        mapAndConfigure("SELECT * FROM exclude_col<caret>;")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings.orEmpty()
        val text = myFixture.editor.document.text
        assertTrue(
            "exclude_columns table function offered or inserted; lookups=${lookups.take(12)} text=$text",
            lookups.contains("exclude_columns") || text.contains("exclude_columns("),
        )
    }

    fun testReservedKeywordCompletes() {
        mapAndConfigure("SELECT * FROM t\nGROU<caret>")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings.orEmpty()
        val text = myFixture.editor.document.text
        assertTrue(
            "GROUP keyword offered or inserted; lookups=${lookups.take(12)} text=$text",
            lookups.contains("GROUP") || text.contains("GROUP"),
        )
    }
}

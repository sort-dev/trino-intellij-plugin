package dev.sort.trino.sql

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import icons.DatabaseIcons

/**
 * Pins the bundled-catalog completion end to end: functions offered with kind icons + return-type
 * text + parens insertion, and reserved words offered as plain CAPS keywords.
 *
 * Fixture rules (duckdb lessons): map the dialect project-wide (null vfile) BEFORE configureByText —
 * a post-configure mapping leaves the completion session on pre-mapping PSI; and a SINGLE matching
 * variant auto-inserts (the lookup never shows), so multi-hit prefixes are used where the lookup
 * list itself must be inspected.
 */
class TrinoCompletionTest : BasePlatformTestCase() {

    private fun mapAndConfigure(text: String) {
        SqlDialectMappings.getInstance(project).setMapping(null, TrinoSqlDialect.INSTANCE)
        myFixture.configureByText("c.sql", text)
    }

    private fun render(el: LookupElement): LookupElementPresentation =
        LookupElementPresentation().also { el.renderElement(it) }

    fun testScalarFunctionOfferedWithFunctionIconAndReturnType() {
        // date_ prefix has several scalar hits (date_add/date_diff/date_trunc/...) -> lookup shows.
        mapAndConfigure("SELECT date_<caret> FROM t;")
        val variants = myFixture.completeBasic()
        assertNotNull("date_ prefix should present a lookup list", variants)
        val trunc = variants!!.firstOrNull { it.lookupString == "date_trunc" }
        assertNotNull("date_trunc offered; got ${variants.map { it.lookupString }.take(15)}", trunc)
        val p = render(trunc!!)
        assertEquals("scalar function -> Function icon", DatabaseIcons.Function, p.icon)
        assertTrue("return type shown as type text: '${p.typeText}'", !p.typeText.isNullOrBlank())
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

    fun testAggregateOfferedWithDistinctAggregateIcon() {
        mapAndConfigure("SELECT approx_<caret> FROM t;")
        val variants = myFixture.completeBasic()
        assertNotNull("approx_ prefix should present a lookup list", variants)
        val agg = variants!!.firstOrNull { it.lookupString == "approx_distinct" }
        assertNotNull("approx_distinct offered; got ${variants.map { it.lookupString }.take(15)}", agg)
        val icon = render(agg!!).icon
        assertEquals("aggregate -> Aggregate icon", DatabaseIcons.Aggregate, icon)
        // "distinct" == a different glyph than scalar functions get.
        assertNotSame("aggregate icon must differ from scalar", DatabaseIcons.Function, icon)
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

    fun testIconMappingCoversEveryKind() {
        // The single source of truth, asserted directly (platform up, so DatabaseIcons is safe).
        assertEquals(DatabaseIcons.Function, TrinoCompletionContributor.iconFor(TrinoFunctionCatalog.Kind.SCALAR))
        assertEquals(DatabaseIcons.Aggregate, TrinoCompletionContributor.iconFor(TrinoFunctionCatalog.Kind.AGGREGATE))
        assertEquals(DatabaseIcons.Function, TrinoCompletionContributor.iconFor(TrinoFunctionCatalog.Kind.WINDOW))
        assertEquals(DatabaseIcons.Table, TrinoCompletionContributor.iconFor(TrinoFunctionCatalog.Kind.TABLE))
        assertEquals(DatabaseIcons.Function, TrinoCompletionContributor.iconFor(TrinoFunctionCatalog.Kind.OTHER))
    }
}

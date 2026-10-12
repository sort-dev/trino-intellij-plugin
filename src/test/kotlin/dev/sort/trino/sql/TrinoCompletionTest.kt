package dev.sort.trino.sql

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.completion.SqlLookupKind
import dev.brikk.house.sql.metadata.FunctionKind
import dev.brikk.house.sql.metadata.FunctionPresentation
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import icons.DatabaseIcons
import javax.swing.Icon

class TrinoCompletionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        setSqlDialectMapping(project, null, TrinoSqlDialect)
    }

    override fun tearDown() {
        try {
            setSqlDialectMapping(project, null, null)
        } finally {
            super.tearDown()
        }
    }

    private fun complete(sql: String): Array<LookupElement> {
        myFixture.configureByText("completion.sql", sql)
        val items = myFixture.completeBasic()
        assertNotNull("Expected a lookup list for $sql", items)
        return items!!
    }

    private fun function(sql: String, name: String): LookupElement =
        complete(sql).single { it.lookupString == name && it.`object` is FunctionPresentation }

    private fun assertPresentation(
        sql: String,
        name: String,
        kind: SqlLookupKind,
        icon: Icon,
        fallback: String,
    ) {
        val item = function(sql, name)
        val entry = item.`object` as FunctionPresentation
        val presentation = LookupElementPresentation().also(item::renderElement)
        assertEquals(name, icon, presentation.icon)
        assertEquals(name, fallback, TrinoCompletionProfile.styles[kind].typeText)
        assertEquals(name, entry.returnType ?: fallback, presentation.typeText)
        assertNull(name, presentation.tailText)
    }

    private fun accept(sql: String, name: String, completionChar: Char = '\n') {
        val item = function(sql, name)
        myFixture.lookup.currentItem = item
        myFixture.finishLookup(completionChar)
    }

    fun testScalarPresentation() {
        assertPresentation("SELECT date_<caret> FROM t;", "date_trunc",
            SqlLookupKind.FUNCTION, DatabaseIcons.Function, "scalar")
    }

    fun testAggregatePresentation() {
        assertPresentation("SELECT approx_<caret> FROM t;", "approx_distinct",
            SqlLookupKind.AGGREGATE, DatabaseIcons.Aggregate, "aggregate")
    }

    fun testWindowPresentation() {
        assertPresentation("SELECT row_num<caret> FROM t;", "row_number",
            SqlLookupKind.WINDOW, DatabaseIcons.Function, "window")
    }

    fun testTableFunctionPresentation() {
        assertPresentation("SELECT * FROM TABLE(ex<caret>);", "exclude_columns",
            SqlLookupKind.TABLE_FUNCTION, DatabaseIcons.Table, "table")
    }

    fun testGrammarFormPresentation() {
        assertPresentation("SELECT ca<caret>", "CAST",
            SqlLookupKind.FUNCTION, DatabaseIcons.Function, "scalar")
        assertPresentation("SELECT * FROM JSON_<caret>", "JSON_TABLE",
            SqlLookupKind.TABLE_FUNCTION, DatabaseIcons.Table, "table")
    }

    fun testFunctionInsertsCallWithCaretInArguments() {
        accept("SELECT ab<caret>", "abs")
        myFixture.checkResult("SELECT abs(<caret>)")
    }

    fun testExistingParenthesisIsReused() {
        accept("SELECT ab<caret>(1)", "abs")
        myFixture.checkResult("SELECT abs(<caret>1)")
    }

    fun testArgumentlessCallPlacesCaretAfterParentheses() {
        accept("SELECT current_<caret>", "current_timezone")
        myFixture.checkResult("SELECT current_timezone()<caret>")
    }

    fun testBareGrammarFormDoesNotInsertParentheses() {
        accept("SELECT current_time<caret>", "CURRENT_TIMESTAMP")
        myFixture.checkResult("SELECT CURRENT_TIMESTAMP<caret>")
    }

    fun testSpecialGrammarFormDoesNotInsertParentheses() {
        accept("SELECT ca<caret>", "CAST")
        myFixture.checkResult("SELECT CAST<caret>")
    }

    fun testTypedParenthesisAcceptsSpecialForm() {
        accept("SELECT ca<caret>", "CAST", '(')
        myFixture.checkResult("SELECT CAST(<caret>)")
    }

    fun testSequenceRetainsScalarAndTableEntries() {
        val entries = complete("SELECT seq<caret>").mapNotNull { it.`object` as? FunctionPresentation }
            .filter { it.name == "sequence" }
        assertEquals(2, entries.size)
        assertEquals(setOf(FunctionKind.SCALAR, FunctionKind.TABLE_VALUED), entries.map { it.kind }.toSet())
    }

    fun testReservedKeywordCompletes() {
        myFixture.configureByText("keywords.sql", "SELECT * FROM t\nGROU<caret>")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings.orEmpty()
        val text = myFixture.editor.document.text
        assertTrue(
            "GROUP keyword offered or inserted; lookups=${lookups.take(12)} text=$text",
            lookups.contains("GROUP") || text.contains("GROUP"),
        )
    }

    fun testKeywordDoesNotDuplicatePlatformLookup() {
        val items = complete("SELECT 1; SE<caret>")
        assertEquals(items.map { it.lookupString }.toString(), 1,
            items.count { it.lookupString.equals("SELECT", ignoreCase = true) })
    }
}

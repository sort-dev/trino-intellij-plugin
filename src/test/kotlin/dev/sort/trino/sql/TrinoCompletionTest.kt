package dev.sort.trino.sql

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.util.Disposer
import com.intellij.sql.formatter.settings.SqlCodeStyleConst
import com.intellij.sql.formatter.settings.SqlCodeStyles
import com.intellij.sql.dialects.BuiltinFunction
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.completion.SqlLookupKind
import dev.brikk.house.sql.intellij.completion.SqlLookupIcons
import dev.brikk.house.sql.metadata.FunctionKind
import dev.brikk.house.sql.metadata.FunctionPresentation
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import icons.DatabaseIcons
import javax.swing.Icon

class TrinoCompletionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        val settings = CodeInsightSettings.getInstance()
        val autocomplete = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        Disposer.register(testRootDisposable) { settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocomplete }
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
            SqlLookupKind.WINDOW, SqlLookupIcons.WindowFunction, "window")
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

    fun testSequenceUsesTheKindOfItsCompletionPosition() {
        val entries = complete("SELECT seq<caret>").mapNotNull { it.`object` as? FunctionPresentation }
            .filter { it.name == "sequence" }
        assertEquals(1, entries.size)
        assertEquals(FunctionKind.SCALAR, entries.single().kind)
        val tables = complete("SELECT * FROM seq<caret>").mapNotNull { it.`object` as? FunctionPresentation }
            .filter { it.name == "sequence" }
        assertEquals(1, tables.size)
        assertEquals(FunctionKind.TABLE_VALUED, tables.single().kind)
    }

    fun testBareTableFunctionInsertsTableWrapper() {
        for (prefix in listOf("FROM ", "SELECT * FROM ")) {
            accept("${prefix}seq<caret>", "sequence")
            myFixture.checkResult("${prefix}TABLE(sequence(<caret>))")
        }
    }

    fun testExistingTableFunctionCallIsWrapped() {
        for (prefix in listOf("FROM ", "SELECT * FROM ")) {
            accept("${prefix}seq<caret>(1, 3)", "sequence")
            myFixture.checkResult("${prefix}TABLE(sequence(<caret>1, 3))")
        }
    }

    fun testInsideTableUsesPlainFunctionCall() {
        for (prefix in listOf("FROM ", "SELECT * FROM ")) {
            accept("${prefix}TABLE(seq<caret>)", "sequence")
            myFixture.checkResult("${prefix}TABLE(sequence(<caret>))")
            accept("${prefix}TABLE(seq<caret>(1, 3))", "sequence")
            myFixture.checkResult("${prefix}TABLE(sequence(<caret>1, 3))")
        }
    }

    fun testUnnestRemainsAnUnwrappedRelation() {
        accept("FROM UNN<caret>", "UNNEST")
        myFixture.checkResult("FROM UNNEST(<caret>)")
        accept("FROM UNN<caret>(ARRAY[1, 2])", "UNNEST")
        myFixture.checkResult("FROM UNNEST(<caret>ARRAY[1, 2])")
    }

    fun testJsonTableRemainsAnUnwrappedGrammarForm() {
        accept("FROM JSON_<caret>", "JSON_TABLE")
        myFixture.checkResult("FROM JSON_TABLE<caret>")
        accept("FROM JSON_<caret>", "JSON_TABLE", '(')
        myFixture.checkResult("FROM JSON_TABLE(<caret>)")
    }

    fun testExtractFromOffersScalarsNotTableFunctions() {
        val entries = complete("SELECT EXTRACT(YEAR FROM seq<caret>)").mapNotNull { it.`object` as? FunctionPresentation }
            .filter { it.name == "sequence" }
        assertEquals(1, entries.size)
        assertEquals(FunctionKind.SCALAR, entries.single().kind)
        accept("SELECT EXTRACT(YEAR FROM ab<caret>)", "abs")
        myFixture.checkResult("SELECT EXTRACT(YEAR FROM abs(<caret>))")
    }

    fun testDelegatedKeywordCaseRespectsCodeStyleAndKeepsFunctionNamesLowerCase() {
        myFixture.configureByText("completion.sql", "SELECT 1; se<caret>")
        val settings = SqlCodeStyles.getSqlSettings(myFixture.file)
        val previous = settings.KEYWORD_CASE
        try {
            for ((case, expected) in listOf(SqlCodeStyleConst.DO_NOT_CHANGE to "SELECT", SqlCodeStyleConst.TO_LOWER to "select", SqlCodeStyleConst.TO_UPPER to "SELECT")) {
                settings.KEYWORD_CASE = case
                val keyword = complete("SELECT 1; se<caret>").single { it.lookupString.equals("SELECT", ignoreCase = true) }
                assertEquals(expected, keyword.lookupString)
                myFixture.lookup.currentItem = keyword
                myFixture.finishLookup('\n')
                assertEquals(expected, myFixture.editor.document.text.substringAfter(';').trim())
                accept("SELECT AB<caret>", "abs")
                myFixture.checkResult("SELECT abs(<caret>)")
            }
        } finally { settings.KEYWORD_CASE = previous }
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

    fun testFunctionsAreNotOfferedInStringsOrComments() {
        for (sql in listOf("SELECT 'ab<caret>'", "SELECT 1 -- ab<caret>", "SELECT 1 /* ab<caret> */", "FROM t |> WHERE status = 'ab<caret>'")) {
            myFixture.configureByText("completion.sql", sql)
            myFixture.completeBasic()
            assertTrue(sql, myFixture.lookupElements.orEmpty().none { it.`object` is FunctionPresentation || it.`object` is BuiltinFunction.Prototype })
        }
    }

    fun testStockBuiltinsDoNotDuplicateMetadataFunctions() {
        val items = complete("SELECT ab<caret>").filter { it.lookupString.equals("abs", ignoreCase = true) }
        assertEquals(1, items.size)
        assertInstanceOf(items.single().`object`, FunctionPresentation::class.java)
    }
}

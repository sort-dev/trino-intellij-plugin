package dev.sort.trino.sql

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.metadata.FunctionKind
import dev.brikk.house.sql.metadata.FunctionPresentation
import dev.sort.trino.setSqlDialectMapping

class TrinoCompletionInsertionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        setSqlDialectMapping(project, null, TrinoSqlDialect.INSTANCE)
    }

    override fun tearDown() {
        try {
            setSqlDialectMapping(project, null, null)
        } finally {
            super.tearDown()
        }
    }

    private fun accept(sql: String, name: String, completionChar: Char = '\n') {
        myFixture.configureByText("completion.sql", sql)
        val items = myFixture.completeBasic().orEmpty()
        val item = items.single { it.lookupString == name && it.`object` is FunctionPresentation }
        myFixture.lookup.currentItem = item
        myFixture.finishLookup(completionChar)
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
        myFixture.configureByText("kinds.sql", "SELECT seq<caret>")
        val entries = myFixture.completeBasic().orEmpty().mapNotNull { it.`object` as? FunctionPresentation }
            .filter { it.name == "sequence" }
        assertEquals(2, entries.size)
        assertEquals(setOf(FunctionKind.SCALAR, FunctionKind.TABLE_VALUED), entries.map { it.kind }.toSet())
    }

    fun testKeywordDoesNotDuplicatePlatformLookup() {
        myFixture.configureByText("keywords.sql", "SELECT * FROM t GRO<caret>")
        val items = myFixture.completeBasic().orEmpty()
        assertEquals(items.map { it.lookupString }.toString(), 1,
            items.count { it.lookupString.equals("GROUP", ignoreCase = true) })
    }
}

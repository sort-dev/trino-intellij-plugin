package dev.sort.trino.sql

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.dialects.postgres.PgLexer
import com.intellij.sql.dialects.base.SqlSyntaxHighlighterFactory
import dev.brikk.house.sql.metadata.KeywordCategory
import dev.brikk.house.sql.metadata.TRINO_METADATA
import com.intellij.sql.psi.SqlTokens
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.lexAll
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping

class TrinoEditorSupportTest : BasePlatformTestCase() {
    private fun configure(sql: String) {
        val file = myFixture.configureByText("editor.sql", sql)
        setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect)
    }

    private fun color(type: IElementType): TextAttributesKey =
        SyntaxHighlighterFactory.getSyntaxHighlighter(TrinoSqlDialect, project, myFixture.file.virtualFile)
            .getTokenHighlights(type).lastOrNull() ?: HighlighterColors.TEXT

    private fun recolors(all: List<HighlightInfo>, sql: String, text: String): List<TextAttributesKey> {
        val start = sql.indexOf(text)
        assertTrue(text, start >= 0)
        return all.filter { it.startOffset == start && it.endOffset == start + text.length }
            .mapNotNull { it.forcedTextAttributesKey }
    }

    fun testMaskedQuotedIdentifiersAndStrings() {
        val sql = "SELECT TRY_CAST(1 AS \"bigint\") FROM t FOR TIMESTAMP AS OF TIMESTAMP '2026-01-01';"
        configure(sql)
        val all = myFixture.doHighlighting()
        assertTrue(recolors(all, sql, "\"bigint\"").contains(color(SqlTokens.SQL_IDENT_DELIMITED)))
        assertFalse(recolors(all, sql, "\"bigint\"").contains(color(SqlTokens.SQL_STRING_TOKEN)))
        assertTrue(recolors(all, sql, "'2026-01-01'").contains(color(SqlTokens.SQL_STRING_TOKEN)))
    }

    fun testCommentsInsideMasksAndGenuineComments() {
        val sql = "SELECT * FROM t PIVOT (sum(x) /* inside */ FOR y IN (1)); /* genuine */ -- unchanged"
        configure(sql)
        val all = myFixture.doHighlighting()
        assertTrue(recolors(all, sql, "/* inside */").contains(color(SqlTokens.SQL_BLOCK_COMMENT)))
        assertTrue(recolors(all, sql, "PIVOT").contains(color(SqlTokens.SQL_KEYWORD_TOKEN)))
        assertTrue(all.none { it.forcedTextAttributesKey != null && it.startOffset >= sql.indexOf("/* genuine */") })
    }

    fun testCollapsedTypesUseIdentifierColors() {
        val sql = "SELECT CAST(x AS ROW(field_name bigint));"
        configure(sql)
        val all = myFixture.doHighlighting()
        assertTrue(recolors(all, sql, "field_name").contains(color(SqlTokens.SQL_IDENT)))
        assertTrue(recolors(all, sql, "ROW").contains(color(SqlTokens.SQL_KEYWORD_TOKEN)))
        assertNotSame(color(SqlTokens.SQL_IDENT), color(SqlTokens.SQL_KEYWORD_TOKEN))
        assertFalse(recolors(all, sql, "field_name").contains(color(SqlTokens.SQL_KEYWORD_TOKEN)))
        assertFalse(recolors(all, sql, "ROW").contains(color(SqlTokens.SQL_IDENT)))
    }

    fun testRoutineBodyKeepsKeywordAndLiteralColors() {
        val sql = "CREATE FUNCTION f() RETURNS bigint BEGIN RETURN 42; END;"
        configure(sql)
        val all = myFixture.doHighlighting()
        assertTrue(recolors(all, sql, "BEGIN").contains(color(SqlTokens.SQL_KEYWORD_TOKEN)))
        assertTrue(recolors(all, sql, "42").contains(color(SqlTokens.SQL_INTEGER_TOKEN)))
    }

    fun testKeywordOverlayUsesCapturedKeywords() {
        configure("SELECT 1;")
        val first = TrinoSyntaxHighlighterFactory().getSyntaxHighlighter(project, myFixture.file.virtualFile)
        val second = TrinoSyntaxHighlighterFactory().getSyntaxHighlighter(project, myFixture.file.virtualFile)
        val reserved = TRINO_RESERVED_KEYWORDS.first { PgLexer().lexAll(it).single().type == SqlTokens.SQL_IDENT }
        val sql = "$reserved ordinary_name"
        val tokens = first.highlightingLexer.lexAll(sql)
        val keyword = tokens.first()
        assertNotSame(SqlTokens.SQL_IDENT, keyword.type)
        assertSame(keyword.type, second.highlightingLexer.lexAll(sql).first().type)
        assertEquals(color(SqlTokens.SQL_KEYWORD_TOKEN), first.getTokenHighlights(keyword.type).last())
        assertSame(SqlTokens.SQL_IDENT, tokens.last().type)
    }

    fun testOverlayOnlyAddsReservedKeywordsAndPreservesPlatformColors() {
        configure("SELECT key, value, count FROM kv;")
        assertEquals(TRINO_METADATA.default.keywords!!.keywords.filter { it.category == KeywordCategory.RESERVED }
            .map { it.word.uppercase() }.toSet(), TRINO_RESERVED_KEYWORDS)
        val overlay = TrinoSyntaxHighlighterFactory().getSyntaxHighlighter(project, myFixture.file.virtualFile)
        val base = object : SqlSyntaxHighlighterFactory.Base(TrinoSqlDialect) {}.getSyntaxHighlighter(project, myFixture.file.virtualFile)
        val sql = myFixture.editor.document.text
        val tokens = overlay.highlightingLexer.lexAll(sql)
        val platformTokens = base.highlightingLexer.lexAll(sql)
        for (word in listOf("key", "value", "count")) {
            val token = tokens.single { sql.substring(it.start, it.end) == word }
            val platformToken = platformTokens.single { sql.substring(it.start, it.end) == word }
            assertSame(platformToken.type, token.type)
            assertFalse(word.uppercase() in TRINO_RESERVED_KEYWORDS)
            assertEquals(base.getTokenHighlights(platformToken.type).toList(), overlay.getTokenHighlights(token.type).toList())
            if (word == "count") assertEquals(color(SqlTokens.SQL_IDENT),
                overlay.getTokenHighlights(token.type).lastOrNull() ?: HighlighterColors.TEXT)
        }
        for (word in listOf("SELECT", "FROM")) {
            val token = tokens.single { sql.substring(it.start, it.end) == word }
            val platformToken = platformTokens.single { sql.substring(it.start, it.end) == word }
            assertNotSame(SqlTokens.SQL_IDENT, platformToken.type)
            assertSame(platformToken.type, token.type)
            assertEquals(base.getTokenHighlights(platformToken.type).toList(), overlay.getTokenHighlights(token.type).toList())
        }
    }

    fun testEnterKeepsMultilineStrings() {
        configure("SELECT 'ab<caret>cd';")
        myFixture.type('\n')
        assertFalse(myFixture.editor.document.text.contains("||"))
        assertTrue(myFixture.editor.document.text.contains("'ab\n"))
        assertTrue(myFixture.editor.document.text.endsWith("cd';"))
    }

    fun testEnterDoesNotSplitRoutineBody() {
        configure("CREATE FUNCTION f() RETURNS bigint BEGIN RETURN <caret>42; END;")
        myFixture.type('\n')
        assertFalse(myFixture.editor.document.text.contains("||"))
        assertTrue(myFixture.editor.document.text.endsWith("42; END;"))
    }

    fun testPlatformCommenterRoundTrip() {
        configure("<caret>SELECT 1;")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        assertEquals("-- SELECT 1;", myFixture.editor.document.text)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        assertEquals("SELECT 1;", myFixture.editor.document.text)
    }

    fun testSubstrateErrorsOnlyHiddenForTrino() {
        val factory = PsiFileFactory.getInstance(project)
        val trino = factory.createFileFromText("trino.sql", TrinoSqlDialect, "SELECT +;")
        val postgres = factory.createFileFromText("pg.sql", PgDialect.INSTANCE, "SELECT +;")
        val trinoError = PsiTreeUtil.findChildrenOfType(trino, PsiErrorElement::class.java).first()
        val pgError = PsiTreeUtil.findChildrenOfType(postgres, PsiErrorElement::class.java).first()
        val filter = TrinoHighlightErrorFilter()
        assertFalse(filter.shouldHighlightErrorElement(trinoError))
        assertTrue(filter.shouldHighlightErrorElement(pgError))
    }
}

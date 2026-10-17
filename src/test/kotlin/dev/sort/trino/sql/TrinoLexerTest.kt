package dev.sort.trino.sql

import com.intellij.database.actions.ShowSqlParametersPanelAction
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.settings.DatabaseSettings
import com.intellij.database.settings.UserPatterns
import com.intellij.sql.psi.SqlTokens
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.brikk.house.sql.intellij.testing.assertRestartsMatchFullLex
import dev.brikk.house.sql.intellij.testing.lexAll
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping

class TrinoLexerTest : BasePlatformTestCase() {
    fun testCommentsDoNotReplacePreviousToken() {
        val sql = "SELECT CAST(x AS /* type */ ROW(a bigint)); SELECT t.* /* aliases */ AS (a) FROM t;"
        val tokens = TrinoLexer().lexAll(sql)
        assertTrue(tokens.any { it.type == SqlTokens.SQL_IDENT && sql.substring(it.start, it.end) == "ROW(a bigint)" })
        assertTrue(tokens.any { it.type == SqlTokens.SQL_BLOCK_COMMENT && sql.substring(it.start, it.end) == "AS (a)" })
        assertRestartsMatchFullLex(::TrinoLexer, sql)
    }

    fun testCastContextEndsAtSemicolon() {
        val sql = "SELECT TRY_CAST(1; SELECT 2 AS value;"
        assertFalse(TrinoLexer().lexAll(sql).any { it.type == SqlTokens.SQL_BLOCK_COMMENT })
        assertRestartsMatchFullLex(::TrinoLexer, sql)
    }

    fun testPivotsAfterMaskedAndCollapsedSpans() {
        val sql = "SELECT * FROM t MATCH_RECOGNIZE (PATTERN (A)) PIVOT (sum(x) FOR y IN (1));"
        assertEquals(listOf("MATCH_RECOGNIZE (PATTERN (A))", "PIVOT (sum(x) FOR y IN (1))"),
            TrinoLexer().lexAll(sql).filter { it.type == SqlTokens.SQL_BLOCK_COMMENT }.map { sql.substring(it.start, it.end) })
        assertRestartsMatchFullLex(::TrinoLexer, sql)
        val collapsed = "SELECT * FROM (VALUES 1, 2) PIVOT (sum(x) FOR y IN (1));"
        val tokens = TrinoLexer().lexAll(collapsed)
        assertTrue(tokens.any { it.type == SqlTokens.SQL_IDENT && collapsed.substring(it.start, it.end) == "(VALUES 1, 2)" })
        assertEquals("PIVOT (sum(x) FOR y IN (1))", tokens.single { it.type == SqlTokens.SQL_BLOCK_COMMENT }.let {
            collapsed.substring(it.start, it.end)
        })
        assertRestartsMatchFullLex(::TrinoLexer, collapsed)
    }

    fun testTerminatedRoutineHasNoTokenLimit() {
        val body = "BEGIN " + "SET x = x + 1; ".repeat(1000) + "RETURN x; END"
        val sql = "CREATE FUNCTION f() RETURNS bigint $body; SELECT 2;"
        val token = TrinoLexer().lexAll(sql).single { it.type == SqlTokens.SQL_STRING_TOKEN }
        assertEquals(body, sql.substring(token.start, token.end))
        assertRestartsMatchFullLex(::TrinoLexer, sql)
    }

    fun testBranchMaskConsumesOnlyOneToken() {
        for (branch in listOf("123", "\"audit\"", "_audit", "audit")) {
            val sql = "SELECT * FROM t @ $branch WHERE id = 1; SELECT 2;"
            val tokens = TrinoLexer().lexAll(sql)
            assertEquals("@ $branch", tokens.single { it.type == SqlTokens.SQL_BLOCK_COMMENT }.let { sql.substring(it.start, it.end) })
            assertTrue(tokens.any { sql.substring(it.start, it.end) == "WHERE" })
            assertRestartsMatchFullLex(::TrinoLexer, sql)
        }
        val incomplete = "SELECT * FROM t @ ; SELECT 2;"
        val tokens = TrinoLexer().lexAll(incomplete)
        assertEquals(2, tokens.count { incomplete.substring(it.start, it.end) == ";" })
        assertRestartsMatchFullLex(::TrinoLexer, incomplete)
    }

    fun testTimeTravelMaskConsumesBalancedCall() {
        for (expression in listOf("CAST('2026-01-01' AS TIMESTAMP)", "CAST (concat('2026-', '01-01') AS TIMESTAMP)")) {
            val sql = "SELECT * FROM t FOR TIMESTAMP AS OF $expression WHERE id = 1; SELECT 2;"
            val tokens = TrinoLexer().lexAll(sql)
            assertEquals("FOR TIMESTAMP AS OF $expression", tokens.single { it.type == SqlTokens.SQL_BLOCK_COMMENT }.let {
                sql.substring(it.start, it.end)
            })
            assertTrue(tokens.any { sql.substring(it.start, it.end) == "WHERE" })
            assertRestartsMatchFullLex(::TrinoLexer, sql)
        }
    }

    fun testUnterminatedRoutineEndsBeforeFirstSemicolon() {
        val sql = "CREATE FUNCTION f() RETURNS bigint BEGIN RETURN 1; SELECT 2;"
        val token = TrinoLexer().lexAll(sql).single { it.type == SqlTokens.SQL_STRING_TOKEN }
        assertEquals("BEGIN RETURN 1", sql.substring(token.start, token.end))
        assertRestartsMatchFullLex(::TrinoLexer, sql)
    }

    fun testRestoreInsideStatement() {
        val sql = "SELECT TRY_CAST(x AS ROW(a bigint)) FROM t FOR VERSION AS OF 1; SELECT 2;"
        val lexer = TrinoLexer()
        lexer.start(sql)
        while (lexer.tokenText != "AS ROW(a bigint)") {
            assertNotNull(lexer.tokenType)
            lexer.advance()
        }
        val position = lexer.currentPosition
        fun remaining() = buildList {
            while (lexer.tokenType != null) {
                add(Triple(lexer.tokenStart, lexer.tokenEnd, lexer.tokenType))
                lexer.advance()
            }
        }
        val expected = remaining()
        lexer.restore(position)
        assertEquals(expected, remaining())
    }

    fun testMaskedConsoleParametersSubmitWholeStatement() {
        val settings = DatabaseSettings.getSettings()
        val saved = settings.userPatterns
        settings.userPatterns = UserPatterns().apply {
            inScripts = true
            inLiterals = true
            processStrings = true
            patterns = mutableListOf(UserPatterns.ParameterPattern("\\$\\{([^\\{\\}]*)\\}", "", "\${name}").enabled(true, true))
        }
        settings.patternCache.clear()
        try {
            val file = myFixture.configureByText("masked-console.sql",
                "SELECT TRY_CAST(1 AS bigint) FROM t FOR VERSION AS OF 1 WHERE id = \${id} AND status = '\${status}' LIMIT \${rows}")
            setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect)
            RecordingConsole(project, file.virtualFile, "h2.unified").use { recording ->
                val storage = ShowSqlParametersPanelAction.getStorage(recording.console)
                storage.putValue("id", "17")
                storage.putValue("status", "open")
                storage.putValue("rows", "3")
                recording.console.executeQueries(myFixture.editor, recording.console.scriptModel, DatabaseSettings.ExecOption())
                assertEquals("SELECT TRY_CAST(1 AS bigint) FROM t FOR VERSION AS OF 1 WHERE id = 17 AND status = 'open' LIMIT 3",
                    (recording.requests.single() as DataRequest.QueryRequest).query.trim())
            }
        } finally {
            settings.userPatterns = saved
            settings.patternCache.clear()
        }
    }
}

package dev.sort.trino.validate

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.trino.sql.TrinoSqlDialect

/**
 * Drives the Stage-2 annotator pipeline directly (collectInformation -> doAnnotate) so assertions
 * are deterministic regardless of whether the light fixture schedules the external-annotator pass
 * (the duckdb annotator was proven the same way). doAnnotate already produces absolute document
 * ranges + messages, so the [TrinoErrorAnnotator.Result] is the full engine verdict for a file.
 */
class TrinoErrorAnnotatorTest : BasePlatformTestCase() {

    private var counter = 0
    private val annotator = TrinoErrorAnnotator()

    private fun trinoFile(sql: String): PsiFile {
        val psi = myFixture.configureByText("t${counter++}.sql", sql)
        SqlDialectMappings.getInstance(project).setMapping(psi.virtualFile, TrinoSqlDialect.INSTANCE)
        return PsiManager.getInstance(project).findFile(psi.virtualFile)!!
    }

    /** collect + doAnnotate over [sql]; returns the source and the engine errors it produced. */
    private fun annotate(sql: String): Pair<String, List<Pair<TextRange, String>>> {
        val info = annotator.collectInformation(trinoFile(sql))
        val result = info?.let { annotator.doAnnotate(it) }
        return sql to (result?.errors ?: emptyList())
    }

    private fun textOf(sql: String, range: TextRange) = sql.substring(range.startOffset, range.endOffset)
    private fun lineOf(sql: String, offset: Int) = sql.substring(0, offset).count { it == '\n' }

    fun testFlagsGarbageAtTheOffendingToken() {
        val (sql, errors) = annotate("SELECT FROM WHERE")
        assertEquals("exactly one engine error", 1, errors.size)
        val (range, message) = errors.single()
        assertEquals("range covers the token Trino pointed at", "FROM", textOf(sql, range))
        assertTrue("message is the engine's own, prefixed: $message", message.startsWith("Trino: "))
    }

    fun testHeadedKeywordSoupFlaggedAtTheOffendingKeyword() {
        // Garbled keywords after a valid head still form one statement; Trino points at the first
        // token that can't follow SELECT.
        val (sql, errors) = annotate("SELECT WHERE FROM")
        assertEquals(1, errors.size)
        assertEquals("WHERE", textOf(sql, errors.single().first))
    }

    fun testHeadlessGibberishIsHandledGracefully() {
        // No recognizable statement head -> the substrate emits loose error tokens and NO
        // SqlStatement node, so there is nothing to hand the parser: no annotation, and — the point
        // of the guard — no crash. Real queries always start with a statement head; this is
        // transient typing state, where silence beats a confusing red.
        assertTrue(annotate("FOO BAR BAZ").second.isEmpty())
    }

    fun testOnlyInvalidStatementsFlaggedInAMixedFile() {
        val (sql, errors) = annotate("SELECT 1;\nSELECT FROM WHERE;\nSELECT 2;")
        assertEquals("only the broken middle statement is flagged", 1, errors.size)
        val range = errors.single().first
        assertEquals("FROM", textOf(sql, range))
        assertTrue("error anchored inside the second statement", range.startOffset > sql.indexOf(';'))
    }

    fun testTrinoOnlySyntaxProducesZeroAnnotations() {
        // The substrate mis-parses every one of these; the engine accepts them -> no red. THE point.
        val (_, errors) = annotate(
            "SELECT filter(ARRAY[1, 2, 3], x -> x > 1);\n" +
                "USE hive.web;\n" +
                "SET SESSION hive.query_max_run_time = '2h';",
        )
        assertTrue("engine-valid Trino must be clean, got: ${errors.map { it.second }}", errors.isEmpty())
    }

    fun testMultiLineStatementLineArithmetic() {
        val (sql, errors) = annotate("SELECT a\nFROM t\nWHERE AND b")
        assertEquals(1, errors.size)
        val range = errors.single().first
        assertEquals("AND", textOf(sql, range))
        assertEquals("mapped onto the third line", 2, lineOf(sql, range.startOffset))
    }

    fun testLeadingCommentPositionAccounted() {
        val (sql, errors) = annotate("-- a leading comment\nSELECT FROM WHERE;")
        assertEquals(1, errors.size)
        val range = errors.single().first
        assertEquals("FROM", textOf(sql, range))
        assertEquals("mapped past the comment onto the second line", 1, lineOf(sql, range.startOffset))
    }

    fun testUnclosedConstructFlaggedNearTheEnd() {
        val (sql, errors) = annotate("SELECT count(*")
        assertEquals(1, errors.size)
        val range = errors.single().first
        assertTrue("range is non-empty", range.endOffset > range.startOffset)
        assertTrue("range stays within the statement text", range.endOffset <= sql.length)
        assertTrue("anchored near the unclosed tail", range.startOffset >= sql.indexOf('('))
    }

    fun testCommentOnlyFileCollectsNothing() {
        assertNull("no statement to validate -> no info", annotator.collectInformation(trinoFile("-- just a comment\n/* another */")))
    }

    fun testValidTrinoStaysCleanThroughTheEditorPath() {
        // Exercises the full highlighting path (registration + apply) when the pass schedules;
        // valid Trino must never surface a "Trino:" error either way.
        val psi = myFixture.configureByText("clean.sql", "SELECT id, count(*) FROM hive.web.users GROUP BY id;")
        SqlDialectMappings.getInstance(project).setMapping(psi.virtualFile, TrinoSqlDialect.INSTANCE)
        val trinoErrors = myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR && it.description?.startsWith("Trino:") == true }
        assertTrue("valid Trino must not raise engine errors, got: ${trinoErrors.map { it.description }}", trinoErrors.isEmpty())
    }
}

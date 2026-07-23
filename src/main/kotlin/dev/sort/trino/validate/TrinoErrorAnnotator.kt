package dev.sort.trino.validate

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlStatement
import dev.sort.trino.sql.TrinoSqlDialect
import io.trino.sql.parser.SqlParser

private val LOG = logger<TrinoErrorAnnotator>()

/**
 * Stage-2 editor surface: each top-level statement in a Trino (Brikk) file is handed to the
 * BUNDLED trino-parser (version 483); every rejection becomes ONE ERROR squiggle at the offending
 * token carrying the engine's own message (`Trino: ...`). Engine-exact, version-exact, zero grammar
 * drift — the doris fe-sql-parser annotator role.
 *
 * This is the authority [dev.sort.trino.sql.TrinoHighlightErrorFilter] promises: the PG substrate's
 * false red on Trino-only syntax is suppressed for display, and THIS supplies the real errors.
 * Unlike the duckdb analog it needs no engine discovery and no data source — the grammar is on the
 * classpath, so validation works on any mapped file.
 *
 * Robustness: the whole pass is time-boxed and wrapped so the annotator never throws; each
 * statement is parsed under its own guard so one bad statement can't blank the rest; broken PSI is
 * validated deliberately (the 3-arg [collectInformation] override — the platform default returns
 * null when the file has [com.intellij.psi.PsiErrorElement]s, which for Trino-only syntax is
 * exactly when the engine's opinion matters most).
 */
class TrinoErrorAnnotator : ExternalAnnotator<TrinoErrorAnnotator.Info, TrinoErrorAnnotator.Result>() {

    /** Top-level statements as (document range, exact text) — the text is what goes to the parser. */
    data class Info(val statements: List<Pair<TextRange, String>>)

    /** Engine-confirmed errors, pre-mapped to absolute document ranges + display messages. */
    data class Result(val errors: List<Pair<TextRange, String>>)

    // The default 3-arg variant returns null when the file has PSI errors — but broken-looking PSI
    // is exactly when the engine's opinion matters (the substrate can't parse Trino-only syntax,
    // and TrinoHighlightErrorFilter only hides those errors for display, it doesn't remove them).
    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? =
        collectInformation(file)

    override fun collectInformation(file: PsiFile): Info? {
        if (!file.language.isKindOf(TrinoSqlDialect.INSTANCE)) return null
        val statements = PsiTreeUtil.findChildrenOfType(file, SqlStatement::class.java)
            .filterNot { it.parent is SqlStatement }        // top-level statements only
            .map { it.textRange to it.text }
            .filter { hasSql(it.second) }                   // skip empty / comment-only spans
        return if (statements.isEmpty()) null else Info(statements)
    }

    override fun doAnnotate(collectedInfo: Info?): Result? {
        val info = collectedInfo ?: return null
        return try {
            val parser = SqlParser()                        // one per pass (stateless, but cheap)
            val deadline = System.nanoTime() + TIME_BUDGET_NANOS
            val errors = ArrayList<Pair<TextRange, String>>()
            for ((range, text) in info.statements) {
                if (System.nanoTime() > deadline) break     // belt+braces whole-file time-box
                if (text.length > MAX_STATEMENT_LEN) continue
                val error = try {
                    TrinoStatementValidator.parse(text, parser)
                } catch (t: Throwable) {
                    LOG.warn("trino-parser threw validating a statement; skipping it", t)
                    null
                } ?: continue
                errors.add(tokenRange(text, range.startOffset, error) to "Trino: ${error.message}")
            }
            Result(errors)
        } catch (t: Throwable) {
            LOG.warn("TrinoErrorAnnotator.doAnnotate failed; suppressing annotations this pass", t)
            null
        }
    }

    override fun apply(file: PsiFile, annotationResult: Result?, holder: AnnotationHolder) {
        val result = annotationResult ?: return
        val docLen = PsiDocumentManager.getInstance(file.project).getDocument(file)?.textLength
            ?: file.textLength
        for ((range, message) in result.errors) {
            val start = range.startOffset.coerceIn(0, docLen)
            val end = range.endOffset.coerceIn(start, docLen)
            if (end <= start) continue                      // stale / degenerate range: skip, never throw
            try {
                holder.newAnnotation(HighlightSeverity.ERROR, message)
                    .range(TextRange(start, end))
                    .create()
            } catch (t: Throwable) {
                LOG.warn("failed to create Trino annotation at [$start,$end)", t)
            }
        }
    }

    /**
     * Absolute document range for [error] within a statement whose exact text is [text] beginning
     * at [statementStart]: map the 1-based line/column to an offset, then extend FORWARD across the
     * token (letters/digits/underscore). Falls back to a single character when the caret sits on
     * punctuation, and to the statement's last character when it points at end-of-input (unclosed
     * constructs report `'<EOF>'`). Purely over [text], so the range needs no document access.
     */
    private fun tokenRange(text: String, statementStart: Int, error: TrinoStatementValidator.ParseError): TextRange {
        val within = (TrinoStatementValidator.offsetOf(text, statementStart, error.line, error.column) - statementStart)
            .coerceIn(0, text.length)
        if (within >= text.length) {
            val lastStart = (text.length - 1).coerceAtLeast(0)
            return TextRange(statementStart + lastStart, statementStart + text.length)
        }
        var end = within
        while (end < text.length && text[end].isTokenChar()) end++
        return if (end > within) {
            TextRange(statementStart + within, statementStart + end)
        } else {
            TextRange(statementStart + within, statementStart + within + 1)
        }
    }

    private fun Char.isTokenChar(): Boolean = isLetterOrDigit() || this == '_'

    /**
     * True when [text] contains something the parser could own — i.e. anything left once SQL
     * comments and whitespace are removed. Conservative: it only strips real comment shapes, so a
     * genuine statement (its leading keyword lives outside any comment) always survives; only
     * all-comment / all-whitespace spans are skipped, which would otherwise draw a false `<EOF>`
     * error from the parser.
     */
    private fun hasSql(text: String): Boolean =
        text.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, " ").isNotBlank()

    private companion object {
        private const val TIME_BUDGET_NANOS = 2_000_000_000L   // ~2s whole-file guard (trino-parser is fast)
        private const val MAX_STATEMENT_LEN = 1_000_000        // never hand a pathological blob to the parser
        private val BLOCK_COMMENT = Regex("(?s)/\\*.*?\\*/")
        private val LINE_COMMENT = Regex("--[^\\n]*")
    }
}

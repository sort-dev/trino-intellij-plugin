package dev.sort.trino.validate

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlStatement
import dev.brikk.house.sql.intellij.pipe.PipeText
import dev.brikk.house.sql.intellij.pipe.PipeTranslation
import dev.brikk.house.sql.intellij.pipe.containsPipe
import dev.brikk.house.sql.intellij.pipe.isEditingEnabled
import dev.brikk.house.sql.intellij.pipe.lower
import dev.brikk.house.sql.intellij.recoverEditorOperation
import dev.brikk.house.sql.parser.ParseError
import dev.brikk.house.sql.shape.SqlFragment
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.pipes.TrinoPipeDialect
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
    data class Info(val statements: List<Pair<TextRange, String>>, val pipesEnabled: Boolean = false,
                    val source: String? = null, val brikk: BrikkTrinoProfile? = null)

    /** Engine-confirmed errors, pre-mapped to absolute document ranges + display messages. */
    data class Result(val errors: List<Pair<TextRange, String>>, val pipesEnabled: Boolean = false,
                      val source: String? = null, val brikk: Boolean = false,
                      val pipeErrors: List<Pair<TextRange, PipeTranslation.Refused>> = emptyList())

    // The default 3-arg variant returns null when the file has PSI errors — but broken-looking PSI
    // is exactly when the engine's opinion matters (the substrate can't parse Trino-only syntax,
    // and TrinoHighlightErrorFilter only hides those errors for display, it doesn't remove them).
    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? =
        collectInformation(file)

    override fun collectInformation(file: PsiFile): Info? {
        if (!file.language.isKindOf(TrinoSqlDialect.INSTANCE)) return null
        val profile = BrikkTrinoProfile.forFile(file)
        if (profile != null) {
            val text = InjectedLanguageManager.getInstance(file.project).getUnescapedText(file)
            return Info(listOf(TextRange(0, text.length) to text), TrinoPipeDialect.isEditingEnabled(file), text, profile)
        }
        val statements = PsiTreeUtil.findChildrenOfType(file, SqlStatement::class.java)
            .filterNot { it.parent is SqlStatement }        // top-level statements only
            .map { it.textRange to it.text }
            .filter { hasSql(it.second) }                   // skip empty / comment-only spans
        return if (statements.isEmpty()) null else Info(statements, TrinoPipeDialect.isEditingEnabled(file), file.text)
    }

    override fun doAnnotate(collectedInfo: Info?): Result? {
        val info = collectedInfo ?: return null
        return recoverEditorOperation {
            val parser = SqlParser()                        // one per pass (stateless, but cheap)
            val deadline = System.nanoTime() + TIME_BUDGET_NANOS
            val errors = ArrayList<Pair<TextRange, String>>()
            val pipeErrors = ArrayList<Pair<TextRange, PipeTranslation.Refused>>()
            for ((range, text) in info.statements) {
                if (System.nanoTime() > deadline) break     // belt+braces whole-file time-box
                if (text.length > MAX_STATEMENT_LEN) continue
                if (info.brikk != null) {
                    if (info.brikk.issues.isEmpty()) {
                        try { SqlFragment(text, "trino").ast } catch (e: ParseError) {
                            val first = e.errors.firstOrNull()
                            val offset = engineOffset(text, first?.line, first?.col)
                            errors.add(TextRange(offset, (offset + 1).coerceAtMost(text.length)) to "Trino Brikk: ${first?.description ?: e.message}")
                        } catch (e: dev.brikk.house.sql.parser.TokenError) {
                            errors.add(TextRange(0, minOf(1, text.length)) to "Trino Brikk: ${e.message}")
                        } catch (e: dev.brikk.house.sql.shape.ShapeError) {
                            errors.add(TextRange(0, minOf(1, text.length)) to "Trino Brikk: ${e.message}")
                        }
                    }
                    continue
                }
                if (info.pipesEnabled && TrinoPipeDialect.containsPipe(text)) {
                    val translated = recoverEditorOperation { TrinoPipeDialect.lower(text) }.getOrElse {
                        PipeTranslation.Refused(null, "PIPE validation failed: ${it.message}")
                    }
                    val error = when (translated) {
                        is PipeTranslation.Refused -> translated
                        is PipeTranslation.Lowered -> translated.blocker?.let { PipeTranslation.Refused(null, it) }
                        else -> null
                    }
                    if (error != null) pipeErrors.add(range to error)
                    continue
                }
                val error = recoverEditorOperation {
                    TrinoStatementValidator.parse(text, parser)
                }.getOrElse { t ->
                    LOG.warn("trino-parser threw validating a statement; skipping it", t)
                    null
                } ?: continue
                errors.add(tokenRange(text, range.startOffset, error) to "Trino: ${error.message}")
            }
            Result(errors, info.pipesEnabled, info.source, info.brikk != null, pipeErrors)
        }.getOrElse { t ->
            LOG.warn("TrinoErrorAnnotator.doAnnotate failed; suppressing annotations this pass", t)
            null
        }
    }

    override fun apply(file: PsiFile, annotationResult: Result?, holder: AnnotationHolder) {
        val result = annotationResult ?: return
        if (result.source != null) {
            val current = if (result.brikk) InjectedLanguageManager.getInstance(file.project).getUnescapedText(file) else file.text
            if (current != result.source || result.pipesEnabled != TrinoPipeDialect.isEditingEnabled(file)) return
        }
        val docLen = PsiDocumentManager.getInstance(file.project).getDocument(file)?.textLength
            ?: file.textLength
        val source = PipeText.of(file)
        val pipeErrors = result.pipeErrors.mapNotNull { (statement, error) ->
            val text = source.text.substring(statement.startOffset, statement.endOffset)
            source.errorRange(statement.startOffset, text, error.range)?.let { it to "${TrinoPipeDialect.label}: ${error.message}" }
        }
        for ((rawRange, message) in result.errors + pipeErrors) {
            val range = if (result.brikk) {
                val manager = InjectedLanguageManager.getInstance(file.project)
                val mapped = TextRange(manager.mapUnescapedOffsetToInjected(file, rawRange.startOffset),
                    manager.mapUnescapedOffsetToInjected(file, rawRange.endOffset))
                manager.intersectWithAllEditableFragments(file, mapped).firstOrNull() ?: continue
            } else rawRange
            val start = range.startOffset.coerceIn(0, docLen)
            val end = range.endOffset.coerceIn(start, docLen)
            if (end <= start) continue                      // stale / degenerate range: skip, never throw
            recoverEditorOperation {
                holder.newAnnotation(HighlightSeverity.ERROR, message)
                    .range(TextRange(start, end))
                    .create()
            }.onFailure { t ->
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

    private fun engineOffset(text: String, line: Int?, col: Int?): Int {
        var offset = text.length - text.trimStart().length
        repeat(((line ?: 1) - 1).coerceAtLeast(0)) {
            offset = text.indexOf('\n', offset).takeIf { it >= 0 }?.plus(1) ?: text.length
        }
        val end = text.indexOf('\n', offset).takeIf { it >= 0 } ?: text.length
        return (offset + ((col ?: 1) - 1).coerceAtLeast(0)).coerceAtMost(end)
            .coerceIn(0, (text.length - 1).coerceAtLeast(0))
    }

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

package dev.sort.trino.validate

import io.trino.sql.parser.ParsingException
import io.trino.sql.parser.SqlParser

/**
 * The engine-exact parse authority behind [TrinoErrorAnnotator]: statement text in, one
 * [ParseError] out — or null when Trino's OWN grammar accepts it (io.trino:trino-parser, version
 * 483, bundled on the plugin classpath). This is the doris fe-sql-parser role, except upstream
 * ships the grammar as a plain Maven artifact: pure JVM, no running engine, no data source, no
 * driver jar — so it validates any mapped file and unit-tests without an IDE fixture.
 *
 * [SqlParser] is stateless (its only instance field is `final`; each `createStatement` builds fresh
 * ANTLR lexer/parser objects internally), hence thread-safe; callers still pass a per-pass instance
 * because they cost nothing to construct.
 */
object TrinoStatementValidator {

    /**
     * A Trino [ParsingException] reduced to what the editor surface needs. [line] and [column] are
     * 1-based and RELATIVE TO the validated statement text (Trino reports `charPositionInLine + 1`,
     * and its precondition guarantees both are `> 0`).
     */
    data class ParseError(val line: Int, val column: Int, val message: String)

    /**
     * Ask Trino's parser to accept [statementText]. Returns the first [ParseError] when the engine
     * rejects it (the parser stops at the first error), or null when it parses. Only
     * [ParsingException] is caught — any OTHER Throwable propagates so the annotator's own guard can
     * log it and skip; a surprise must never masquerade as a syntax error.
     */
    fun parse(statementText: String, parser: SqlParser = SqlParser()): ParseError? =
        try {
            parser.createStatement(statementText)
            null
        } catch (e: ParsingException) {
            ParseError(e.lineNumber, e.columnNumber, e.errorMessage ?: e.message ?: "syntax error")
        }

    /**
     * Map a 1-based ([line], [column]) reported by Trino RELATIVE to [statementText] onto an
     * absolute document offset, given the statement begins at [statementStart]. Pure arithmetic
     * over the exact text handed to the parser: walk to the start of [line] by counting `'\n'`
     * (IDE documents — and therefore the PSI text we pass — are `'\n'`-normalized, matching ANTLR's
     * own line counting), then add `column - 1`. A caret past the last line or column clamps to the
     * end of the statement text, never beyond it.
     */
    fun offsetOf(statementText: String, statementStart: Int, line: Int, column: Int): Int {
        var idx = 0
        var currentLine = 1
        while (currentLine < line) {
            val nl = statementText.indexOf('\n', idx)
            if (nl < 0) {
                idx = statementText.length
                break
            }
            idx = nl + 1
            currentLine++
        }
        val within = (idx + (column - 1)).coerceIn(0, statementText.length)
        return statementStart + within
    }
}

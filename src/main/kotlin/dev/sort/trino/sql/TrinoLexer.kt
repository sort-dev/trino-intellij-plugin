package dev.sort.trino.sql

import com.intellij.lexer.Lexer
import com.intellij.lexer.LookAheadLexer
import com.intellij.sql.dialects.postgres.PgLexer
import com.intellij.sql.psi.SqlTokens

/**
 * Token-layer bridge from Trino syntax to the PG grammar (the DorisLexer/DuckdbLexer technique,
 * Trino edition). Each rule either COLLAPSES a Trino-only span into one token the grammar accepts
 * as a unit, or MASKS a droppable Trino-only modifier as a comment so the surrounding statement
 * keeps its full structured parse. Rules are expression-hole-safe: spans in expression position
 * become one STRING token (a valid expression), spans in table/type position become one IDENT,
 * and only modifiers whose removal leaves a PG-valid statement become comments. Every rule below
 * was demanded by a red census family (see TrinoCensusScoreboardTest) — nothing speculative.
 *
 *  COLLAPSES (string = expression position, ident = table/type position):
 *  - `BEGIN ... END` routine body when FUNCTION was seen in the statement → one SQL_STRING_TOKEN.
 *    THE load-bearing rule: bodies carry `;` INSIDE the statement, so without this collapse no
 *    statement boundary survives a SQL routine. Balanced via a construct stack (BEGIN/CASE push;
 *    IF/WHILE/REPEAT/LOOP push with an is-it-a-function-call raw probe; `END <kind>` pops its
 *    kind, bare END pops CASE-expressions then BEGIN).
 *  - `ROW(...)`/`MAP(...)`/`ARRAY(...)` as a TYPE (after `AS` or `TYPE`), any nesting → one
 *    SQL_IDENT (CAST targets, SET DATA TYPE).
 *  - `JSON_TABLE(...)` whole invocation → one SQL_IDENT (only legal in FROM position).
 *  - `JSON_QUERY/JSON_VALUE/JSON_EXISTS/JSON_OBJECT/JSON_ARRAY(...)` → one SQL_STRING_TOKEN, but
 *    ONLY when the argument list carries SQL/JSON clause syntax PG cannot hold (ON ERROR/EMPTY,
 *    RETURNING, PASSING, WRAPPER, QUOTES, KEY..VALUE, `'k' : v`) — plain calls stay structured.
 *  - `(VALUES <scalar>, ...)` in FROM/JOIN/`,` position → one SQL_IDENT; PG requires
 *    parenthesized row constructors, Trino allows bare scalar rows. Parenthesized-row VALUES
 *    (PG-fine) are probed and left alone.
 *  - `LATERAL (VALUES <scalar>, ...)` → one SQL_IDENT (LATERAL + group together).
 *  - `ALL/ANY/SOME (VALUES <scalar>, ...)` quantified comparison → one SQL_STRING_TOKEN.
 *  - bare `DOUBLE` not followed by PRECISION → SQL_IDENT (Trino type; PG only knows the pair).
 *  - `ROW::method(args)` type-method calls → one SQL_STRING_TOKEN (Trino has no `::` cast —
 *    in this dialect `::` only ever means the 483 type-method syntax).
 *
 *  MASKS (comment; recolored by [TrinoMaskedSpanRecolorAnnotator]):
 *  - `MATCH_RECOGNIZE ( ... )` after a relation → whole clause masked (row-pattern grammar).
 *  - `PIVOT ( ... )` after a relation → whole clause masked.
 *  - `FOR TIMESTAMP|VERSION AS OF <atom>` time travel → masked (droppable table suffix).
 *  - `AT LOCAL` → masked (platform PG grammar predates it).
 *  - `ON OVERFLOW ERROR|TRUNCATE ['...'] [WITH|WITHOUT COUNT]` (listagg) → masked.
 *  - `TRY_CAST(x AS t)`'s ` AS t` tail → masked (call parses as 1-arg fn; duckdb port).
 *  - `.* AS (alias, ...)` star column aliases → the `AS (...)` tail masked.
 *  - `@ branch` branch-qualified table suffix → masked.
 *
 * State tracking (parenDepth / lastMeaningful / statementHead / sawFunction) mirrors DuckdbLexer;
 * masks deliberately do NOT touch lastMeaningful so statement-head tracking never sees them.
 * This lexer also feeds the editor highlighter, hence the recolor annotator for masked spans.
 */
class TrinoLexer : LookAheadLexer(PgLexer()) {

    private var parenDepth = 0
    private var lastMeaningful: String? = null
    private var statementHead: String? = null
    private var sawFunction = false
    private val tryCastAsDepths = ArrayDeque<Int>()

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        parenDepth = 0
        lastMeaningful = null
        statementHead = null
        sawFunction = false
        tryCastAsDepths.clear()
        super.start(buffer, startOffset, endOffset, initialState)
    }

    override fun lookAhead(baseLexer: Lexer) {
        val text = baseLexer.tokenText
        val upper = text.uppercase()
        when {
            upper == "BEGIN" && sawFunction && parenDepth == 0 -> {
                collapseRoutineBody(baseLexer)
                return
            }
            upper == "TRY_CAST" && nextIs(baseLexer, "(") -> {
                tryCastAsDepths.addLast(parenDepth + 1)
                trackStructure(text); noteToken(upper)
                super.lookAhead(baseLexer)
                return
            }
            upper == "AS" && tryCastAsDepths.isNotEmpty() && parenDepth == tryCastAsDepths.last() -> {
                maskUntilCloseParenAtCurrentDepth(baseLexer)
                return
            }
            upper == "JSON_TABLE" && nextIs(baseLexer, "(") -> {
                collapseCall(baseLexer, SqlTokens.SQL_IDENT)
                return
            }
            upper in JSON_FN_HEADS && nextIs(baseLexer, "(") && parenHasJsonClauseSyntax(baseLexer) -> {
                collapseCall(baseLexer, SqlTokens.SQL_STRING_TOKEN)
                return
            }
            upper == "ROW" && nextIs(baseLexer, "::") -> {
                collapseRowMethodCall(baseLexer)
                return
            }
            upper in TYPE_COLLAPSE_HEADS && nextIs(baseLexer, "(") &&
                (lastMeaningful == "AS" || lastMeaningful == "TYPE") -> {
                collapseCall(baseLexer, SqlTokens.SQL_IDENT)
                return
            }
            upper == "DOUBLE" && !wordsFollow(baseLexer, "PRECISION") -> {
                advanceAs(baseLexer, SqlTokens.SQL_IDENT)
                noteToken(upper)
                return
            }
            upper == "MATCH_RECOGNIZE" && nextIs(baseLexer, "(") -> {
                maskThroughMatchingParen(baseLexer)
                return
            }
            upper == "PIVOT" && nextIs(baseLexer, "(") &&
                lastMeaningful?.firstOrNull()?.isLetter() == true && lastMeaningful !in CALL_POSITIONS -> {
                maskThroughMatchingParen(baseLexer)
                return
            }
            upper == "LATERAL" && parenAfterLeadsScalarValues(baseLexer) -> {
                collapseThroughGroup(baseLexer, SqlTokens.SQL_IDENT) // LATERAL + (VALUES ...) as one ident
                return
            }
            text == "(" && lastMeaningful in VALUES_TABLE_POSITIONS && parenLeadsScalarValues(baseLexer) -> {
                collapseParenGroup(baseLexer, SqlTokens.SQL_IDENT)
                return
            }
            upper in QUANTIFIERS && parenAfterLeadsScalarValues(baseLexer) -> {
                collapseThroughGroup(baseLexer, SqlTokens.SQL_STRING_TOKEN) // ALL (VALUES ...) as one expr
                return
            }
            upper == "FOR" && (wordsFollow(baseLexer, "TIMESTAMP", "AS", "OF") || wordsFollow(baseLexer, "VERSION", "AS", "OF")) -> {
                maskTimeTravel(baseLexer)
                return
            }
            upper == "AT" && wordsFollow(baseLexer, "LOCAL") -> {
                maskWords(baseLexer, 2)
                return
            }
            upper == "ON" && wordsFollow(baseLexer, "OVERFLOW") -> {
                maskOverflowClause(baseLexer)
                return
            }
            upper == "AS" && lastMeaningful == "*" && nextIs(baseLexer, "(") -> {
                maskThroughMatchingParen(baseLexer) // `.* AS (alias, ...)` — Trino star aliases
                return
            }
            text == "@" -> {
                maskWords(baseLexer, 1) // "@ branch" — the @ plus the branch word
                return
            }
        }
        trackStructure(text)
        noteToken(if (text == "*") "*" else upper)
        super.lookAhead(baseLexer)
    }

    // --- routine-body collapse -------------------------------------------------------------

    /**
     * From a routine-context BEGIN: consume the whole body through its balancing END as ONE
     * string token, so the `;` terminators INSIDE the body never end the statement. Stack frames:
     * 'B' BEGIN, 'C' CASE (statement or expression), 'I' IF, 'W' WHILE, 'R' REPEAT, 'L' LOOP.
     */
    private fun collapseRoutineBody(base: Lexer) {
        val stack = ArrayDeque<Char>()
        stack.addLast('B')
        base.advance() // BEGIN
        var guard = 4096
        while (base.tokenType != null && guard-- > 0 && stack.isNotEmpty()) {
            val t = base.tokenText
            val u = t.uppercase()
            when (u) {
                "BEGIN" -> { stack.addLast('B'); base.advance() }
                "CASE" -> { stack.addLast('C'); base.advance() }
                "IF" -> { if (ifIsStatement(base)) stack.addLast('I'); base.advance() }
                "WHILE" -> { stack.addLast('W'); base.advance() }
                "REPEAT" -> { if (!nextIs(base, "(")) stack.addLast('R'); base.advance() }
                "LOOP" -> { stack.addLast('L'); base.advance() }
                "END" -> {
                    base.advance() // END
                    skipWs(base)
                    val kind = when (base.tokenText.uppercase()) {
                        "IF" -> 'I'; "WHILE" -> 'W'; "REPEAT" -> 'R'; "LOOP" -> 'L'; "CASE" -> 'C'
                        else -> null
                    }
                    if (kind != null && stack.contains(kind)) {
                        base.advance() // the kind word of `END <kind>`
                        // pop through mismatched frames leniently down to the matching kind
                        while (stack.isNotEmpty() && stack.removeLast() != kind) Unit
                    } else {
                        // bare END: closes the innermost CASE-expression, else the BEGIN
                        if (stack.isNotEmpty()) stack.removeLast()
                    }
                }
                else -> base.advance()
            }
        }
        addToken(base.tokenStart, SqlTokens.SQL_STRING_TOKEN)
        noteToken("ROUTINE_BODY")
    }

    /** Raw-buffer probe: this IF opens a statement (condition [+ parens] then THEN), not a call. */
    private fun ifIsStatement(base: Lexer): Boolean {
        val seq = base.bufferSequence
        var i = base.tokenEnd
        while (i < seq.length && seq[i].isWhitespace()) i++
        if (i >= seq.length) return false
        if (seq[i] != '(') return true // statement-IF conditions may be bare; if() calls never are
        var depth = 0
        var quote = false
        while (i < seq.length) {
            val c = seq[i]
            when {
                quote -> if (c == '\'') quote = false
                c == '\'' -> quote = true
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) { i++; break }
                }
            }
            i++
        }
        while (i < seq.length && seq[i].isWhitespace()) i++
        return i + 4 <= seq.length && seq.subSequence(i, i + 4).toString().equals("THEN", true) &&
            (i + 4 == seq.length || !seq[i + 4].isLetterOrDigit())
    }

    // --- span collapsers -------------------------------------------------------------------

    /** head word + its balanced `(...)` group → ONE token of [type]. */
    private fun collapseCall(base: Lexer, type: com.intellij.psi.tree.IElementType) {
        base.advance() // head word
        skipWs(base)
        consumeBalancedParens(base)
        addToken(base.tokenStart, type)
        noteToken("COLLAPSED_CALL")
    }

    /** current word + following `(...)` group (LATERAL/ALL/ANY/SOME forms) → ONE token of [type]. */
    private fun collapseThroughGroup(base: Lexer, type: com.intellij.psi.tree.IElementType) {
        base.advance() // the word
        skipWs(base)
        consumeBalancedParens(base)
        addToken(base.tokenStart, type)
        noteToken("COLLAPSED_GROUP")
    }

    /** the `(` we are ON, through its match → ONE token of [type]. */
    private fun collapseParenGroup(base: Lexer, type: com.intellij.psi.tree.IElementType) {
        consumeBalancedParens(base)
        addToken(base.tokenStart, type)
        noteToken("COLLAPSED_GROUP")
    }

    /** `ROW::method(args)` type-method call (Trino has no `::` cast — `::` IS the method call) →
     *  ONE string token (expression position). */
    private fun collapseRowMethodCall(base: Lexer) {
        base.advance() // ROW
        var guard = 4
        while (base.tokenType != null && guard-- > 0 && base.tokenText != "(") base.advance() // ::, method
        consumeBalancedParens(base)
        addToken(base.tokenStart, SqlTokens.SQL_STRING_TOKEN)
        noteToken("COLLAPSED_CALL")
    }

    private fun consumeBalancedParens(base: Lexer) {
        var depth = 0
        do {
            when (base.tokenText) {
                "(" -> depth++
                ")" -> depth--
            }
            base.advance()
        } while (base.tokenType != null && depth > 0)
    }

    // --- comment masks ---------------------------------------------------------------------

    /** Mask the next [words] letter-words (plus interleaved punctuation/ws) as one comment. */
    private fun maskWords(base: Lexer, words: Int) {
        var remaining = words
        while (base.tokenType != null && remaining > 0) {
            if (base.tokenText.firstOrNull()?.isLetter() == true) remaining--
            base.advance()
        }
        addToken(base.tokenStart, SqlTokens.SQL_BLOCK_COMMENT)
        // deliberately NOT noting: masked modifiers must be invisible to head tracking
    }

    /** Mask head word + its balanced `(...)` group (MATCH_RECOGNIZE/PIVOT clauses). */
    private fun maskThroughMatchingParen(base: Lexer) {
        base.advance() // head word
        skipWs(base)
        consumeBalancedParens(base)
        addToken(base.tokenStart, SqlTokens.SQL_BLOCK_COMMENT)
        noteToken("MASKED")
    }

    /** Inside TRY_CAST(x AS t): mask from AS up to (not including) the call's close paren. */
    private fun maskUntilCloseParenAtCurrentDepth(base: Lexer) {
        var depth = 0
        base.advance() // AS
        while (base.tokenType != null) {
            val t = base.tokenText
            if (t == "(") depth++
            if (t == ")") {
                if (depth == 0) break
                depth--
            }
            if (t == ";") break
            base.advance()
        }
        addToken(base.tokenStart, SqlTokens.SQL_BLOCK_COMMENT)
        noteToken("MASKED")
    }

    /** Mask `FOR TIMESTAMP|VERSION AS OF [TIMESTAMP|DATE] <atom|(expr)>` (time travel). */
    private fun maskTimeTravel(base: Lexer) {
        maskWordsNoToken(base, 4) // FOR + TIMESTAMP|VERSION + AS + OF — consumed, not yet emitted
        skipWs(base)
        val u = base.tokenText.uppercase()
        if (u == "TIMESTAMP" || u == "DATE") base.advance()
        skipWs(base)
        when {
            base.tokenText == "(" -> consumeBalancedParens(base)
            base.tokenType != null -> base.advance() // the atom: string, number, or ident
        }
        addToken(base.tokenStart, SqlTokens.SQL_BLOCK_COMMENT)
        noteToken("MASKED")
    }

    /** Mask `ON OVERFLOW ERROR` / `ON OVERFLOW TRUNCATE ['...'] [WITH|WITHOUT COUNT]` (listagg). */
    private fun maskOverflowClause(base: Lexer) {
        maskWordsNoToken(base, 2) // ON OVERFLOW
        skipWs(base)
        when (base.tokenText.uppercase()) {
            "ERROR" -> base.advance()
            "TRUNCATE" -> {
                base.advance()
                skipWs(base)
                if (base.tokenText.firstOrNull() == '\'') base.advance() // the filler string
                skipWs(base)
                val w = base.tokenText.uppercase()
                if (w == "WITH" || w == "WITHOUT") {
                    base.advance()
                    skipWs(base)
                    if (base.tokenText.uppercase() == "COUNT") base.advance()
                }
            }
        }
        addToken(base.tokenStart, SqlTokens.SQL_BLOCK_COMMENT)
        noteToken("MASKED")
    }

    /** Advance past [words] letter-words without emitting (helper for compound masks). */
    private fun maskWordsNoToken(base: Lexer, words: Int) {
        var remaining = words
        while (base.tokenType != null && remaining > 0) {
            if (base.tokenText.firstOrNull()?.isLetter() == true) remaining--
            base.advance()
        }
    }

    // --- probes (non-consuming; raw buffer, cheap) -----------------------------------------

    private fun nextIs(base: Lexer, expected: String): Boolean {
        val seq = base.bufferSequence
        var i = base.tokenEnd
        while (i < seq.length && seq[i].isWhitespace()) i++
        return i < seq.length && seq.startsWith(expected, i)
    }

    /** The following letter-words (case-insensitive), skipping whitespace, match [words] in order. */
    private fun wordsFollow(base: Lexer, vararg words: String): Boolean {
        val seq = base.bufferSequence
        var i = base.tokenEnd
        for (w in words) {
            while (i < seq.length && seq[i].isWhitespace()) i++
            val end = i + w.length
            if (end > seq.length || !seq.subSequence(i, end).toString().equals(w, ignoreCase = true)) return false
            if (end < seq.length && (seq[end].isLetterOrDigit() || seq[end] == '_')) return false
            i = end
        }
        return true
    }

    /** After the current token: `(` whose first word is VALUES followed by a SCALAR row (PG-fine
     *  parenthesized rows are left alone). */
    private fun parenAfterLeadsScalarValues(base: Lexer): Boolean {
        val seq = base.bufferSequence
        var i = base.tokenEnd
        while (i < seq.length && seq[i].isWhitespace()) i++
        return leadsScalarValuesAt(seq, i)
    }

    /** The `(` AT the current token leads with scalar-row VALUES. */
    private fun parenLeadsScalarValues(base: Lexer): Boolean =
        leadsScalarValuesAt(base.bufferSequence, base.tokenStart)

    private fun leadsScalarValuesAt(seq: CharSequence, parenPos: Int): Boolean {
        var i = parenPos
        if (i >= seq.length || seq[i] != '(') return false
        i++
        while (i < seq.length && seq[i].isWhitespace()) i++
        val end = i + 6
        if (end > seq.length || !seq.subSequence(i, end).toString().equals("VALUES", true)) return false
        if (end < seq.length && (seq[end].isLetterOrDigit() || seq[end] == '_')) return false
        i = end
        while (i < seq.length && seq[i].isWhitespace()) i++
        // scalar row = anything but a parenthesized row constructor
        return i < seq.length && seq[i] != '('
    }

    /** The `(...)` after a JSON fn head carries SQL/JSON clause words PG cannot hold. */
    private fun parenHasJsonClauseSyntax(base: Lexer): Boolean {
        val seq = base.bufferSequence
        var i = base.tokenEnd
        while (i < seq.length && seq[i].isWhitespace()) i++
        if (i >= seq.length || seq[i] != '(') return false
        var depth = 0
        var quote = false
        while (i < seq.length) {
            val c = seq[i]
            when {
                quote -> if (c == '\'') quote = false
                c == '\'' -> quote = true
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return false
                }
                c == ':' && depth >= 1 &&
                    (i + 1 >= seq.length || seq[i + 1] != ':') && (i == 0 || seq[i - 1] != ':') -> return true
                c.isLetter() && (i == 0 || !seq[i - 1].isLetterOrDigit() && seq[i - 1] != '_') -> {
                    var j = i
                    while (j < seq.length && (seq[j].isLetterOrDigit() || seq[j] == '_')) j++
                    if (seq.subSequence(i, j).toString().uppercase() in JSON_CLAUSE_WORDS) return true
                    i = j - 1
                }
            }
            i++
        }
        return false
    }

    // --- shared machinery ------------------------------------------------------------------

    private fun skipWs(base: Lexer) {
        while (base.tokenType != null && base.tokenText.isBlank()) base.advance()
    }

    private fun trackStructure(text: String?) {
        when (text) {
            "(" -> parenDepth++
            ")" -> {
                parenDepth--
                while (tryCastAsDepths.isNotEmpty() && tryCastAsDepths.last() > parenDepth) {
                    tryCastAsDepths.removeLast()
                }
            }
            ";" -> parenDepth = 0
        }
    }

    private fun noteToken(upperOrSymbol: String?) {
        val t = upperOrSymbol ?: return
        if (t.isBlank()) return
        if (statementHead == null && t.firstOrNull()?.isLetter() == true) statementHead = t
        if (t == "FUNCTION") sawFunction = true
        if (t == ";") {
            statementHead = null
            sawFunction = false
        }
        lastMeaningful = t
    }

    private companion object {
        private val TYPE_COLLAPSE_HEADS = setOf("ROW", "MAP", "ARRAY")
        private val JSON_FN_HEADS = setOf("JSON_QUERY", "JSON_VALUE", "JSON_EXISTS", "JSON_OBJECT", "JSON_ARRAY")
        private val JSON_CLAUSE_WORDS = setOf(
            "PASSING", "RETURNING", "WRAPPER", "QUOTES", "ERROR", "EMPTY",
            "KEY", "ABSENT", "UNCONDITIONAL", "CONDITIONAL",
        )
        private val QUANTIFIERS = setOf("ALL", "ANY", "SOME")
        private val VALUES_TABLE_POSITIONS = setOf("FROM", "JOIN", ",")
        private val CALL_POSITIONS = setOf("SELECT", ",", "AS", "(")
    }
}

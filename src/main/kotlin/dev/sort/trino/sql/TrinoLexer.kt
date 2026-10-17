package dev.sort.trino.sql

import com.intellij.lexer.Lexer
import com.intellij.sql.dialects.postgres.PgLexer
import com.intellij.sql.psi.SqlTokens
import dev.brikk.house.sql.intellij.lexer.CastTargetMask
import dev.brikk.house.sql.intellij.lexer.MaskingSqlLexer

class TrinoLexer : MaskingSqlLexer(PgLexer(), CastTargetMask()) {
    private var sawFunction = false

    override fun resetStatement() {
        sawFunction = false
    }

    override fun onNote(token: String) {
        if (token == "FUNCTION") sawFunction = true
    }

    override fun rule(base: Lexer, upper: String): Boolean {
        // Collapse routine bodies, type targets and scalar VALUES; mask droppable table modifiers.
        // JSON calls collapse only with SQL/JSON clauses, leaving ordinary calls structured.
        // PIVOT follows a relation-like token, including masked or collapsed spans, not a call position.
        when {
            upper == "BEGIN" && sawFunction && parenDepth == 0 -> collapseRoutineBody(base)
            upper == "JSON_TABLE" && nextIs(base, "(") ->
                collapseWithGroup(base, SqlTokens.SQL_IDENT, "COLLAPSED_CALL")
            upper in JSON_FN_HEADS && nextIs(base, "(") && parenHasJsonClauseSyntax(base) ->
                collapseWithGroup(base, SqlTokens.SQL_STRING_TOKEN, "COLLAPSED_CALL")
            upper == "ROW" && nextIs(base, "::") -> collapseRowMethodCall(base)
            upper in TYPE_COLLAPSE_HEADS && nextIs(base, "(") &&
                lastMeaningful in TYPE_POSITIONS ->
                collapseWithGroup(base, SqlTokens.SQL_IDENT, "COLLAPSED_CALL")
            upper == "DOUBLE" && !wordsFollow(base, "PRECISION") -> emitAs(base, SqlTokens.SQL_IDENT)
            upper == "MATCH_RECOGNIZE" && nextIs(base, "(") -> maskWithGroup(base)
            upper == "PIVOT" && nextIs(base, "(") &&
                lastMeaningful?.firstOrNull()?.isLetter() == true && lastMeaningful !in CALL_POSITIONS ->
                maskWithGroup(base)
            upper == "LATERAL" && parenAfterLeadsScalarValues(base) ->
                collapseWithGroup(base, SqlTokens.SQL_IDENT, "COLLAPSED_GROUP")
            upper == "(" && lastMeaningful in VALUES_TABLE_POSITIONS &&
                leadsScalarValuesAt(base.bufferSequence, base.tokenStart) -> {
                consumeBalanced(base)
                span(base, SqlTokens.SQL_IDENT, "COLLAPSED_GROUP")
            }
            upper in QUANTIFIERS && parenAfterLeadsScalarValues(base) ->
                collapseWithGroup(base, SqlTokens.SQL_STRING_TOKEN, "COLLAPSED_GROUP")
            upper == "FOR" && (wordsFollow(base, "TIMESTAMP", "AS", "OF") ||
                wordsFollow(base, "VERSION", "AS", "OF")) -> maskTimeTravel(base)
            upper == "AT" && wordsFollow(base, "LOCAL") -> maskWords(base, 2)
            upper == "ON" && wordsFollow(base, "OVERFLOW") -> maskOverflowClause(base)
            upper == "AS" && lastMeaningful == "*" && nextIs(base, "(") -> maskWithGroup(base)
            upper == "@" -> maskBranch(base)
            else -> return false
        }
        return true
    }

    private fun collapseRowMethodCall(base: Lexer) {
        base.advance()
        var guard = 4
        while (base.tokenType != null && guard-- > 0 && base.tokenText != "(") base.advance()
        consumeBalanced(base)
        span(base, SqlTokens.SQL_STRING_TOKEN, "COLLAPSED_CALL")
    }

    private fun maskTimeTravel(base: Lexer) {
        consumeWords(base, 4)
        skipWhitespaceTokens(base)
        val upper = base.tokenText.uppercase()
        if (upper == "TIMESTAMP" || upper == "DATE") base.advance()
        skipWhitespaceTokens(base)
        when {
            base.tokenText == "(" -> consumeBalanced(base)
            base.tokenText.firstOrNull()?.isLetter() == true && nextIs(base, "(") -> {
                base.advance()
                skipWhitespaceTokens(base)
                consumeBalanced(base)
            }
            base.tokenType != null -> base.advance()
        }
        span(base, SqlTokens.SQL_BLOCK_COMMENT, MASKED)
    }

    private fun maskBranch(base: Lexer) {
        base.advance()
        skipWhitespaceTokens(base)
        if (base.tokenType != null && base.tokenText != ";") base.advance()
        span(base, SqlTokens.SQL_BLOCK_COMMENT, null)
    }

    private fun maskOverflowClause(base: Lexer) {
        consumeWords(base, 2)
        skipWhitespaceTokens(base)
        when (base.tokenText.uppercase()) {
            "ERROR" -> base.advance()
            "TRUNCATE" -> {
                base.advance()
                skipWhitespaceTokens(base)
                if (base.tokenText.firstOrNull() == '\'') base.advance()
                skipWhitespaceTokens(base)
                val word = base.tokenText.uppercase()
                if (word == "WITH" || word == "WITHOUT") {
                    base.advance()
                    skipWhitespaceTokens(base)
                    if (base.tokenText.uppercase() == "COUNT") base.advance()
                }
            }
        }
        span(base, SqlTokens.SQL_BLOCK_COMMENT, MASKED)
    }

    private fun parenAfterLeadsScalarValues(base: Lexer): Boolean =
        leadsScalarValuesAt(base.bufferSequence, offsetAfterWhitespace(base, base.tokenEnd))

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
        return i < seq.length && seq[i] != '('
    }

    private fun parenHasJsonClauseSyntax(base: Lexer): Boolean {
        val seq = base.bufferSequence
        var i = offsetAfterWhitespace(base, base.tokenEnd)
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

    private companion object {
        val TYPE_COLLAPSE_HEADS = setOf("ROW", "MAP", "ARRAY")
        val TYPE_POSITIONS = setOf("AS", "TYPE")
        val JSON_FN_HEADS = setOf("JSON_QUERY", "JSON_VALUE", "JSON_EXISTS", "JSON_OBJECT", "JSON_ARRAY")
        val JSON_CLAUSE_WORDS = setOf(
            "PASSING", "RETURNING", "WRAPPER", "QUOTES", "ERROR", "EMPTY",
            "KEY", "ABSENT", "UNCONDITIONAL", "CONDITIONAL",
        )
        val QUANTIFIERS = setOf("ALL", "ANY", "SOME")
        val VALUES_TABLE_POSITIONS = setOf("FROM", "JOIN", ",")
        val CALL_POSITIONS = setOf("SELECT", ",", "AS", "(")
    }
}

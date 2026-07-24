package dev.sort.trino.sql

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

/**
 * Re-colors [TrinoLexer]'s MASKED and string-COLLAPSED spans so they don't render as
 * comments/strings (the duckdb DuckdbMaskedSpanRecolorAnnotator port — same root cause: the
 * platform's SqlSyntaxHighlighter builds its highlighting lexer from OUR ParserDefinition, so
 * parser-side masking is also what the editor colors).
 *
 * A masked span is recognizable with zero false positives: a [PsiComment] whose text does not
 * start with a real comment introducer (slash-star or `--`) — the lexer only emits such
 * "comments" for masked SQL. A collapsed span is a string token whose text doesn't start with a
 * quote (routine bodies, quantified VALUES, JSON calls with clause syntax). Each word inside gets
 * keyword/identifier color, literals their literal colors, punctuation plain text.
 */
class TrinoMaskedSpanRecolorAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!element.containingFile.language.isKindOf(TrinoSqlDialect.INSTANCE)) return
        val text = element.text
        val isMaskComment = element is PsiComment && text.isNotEmpty() && !isRealComment(text)
        val isCollapsedString = element.firstChild == null && element !is PsiComment &&
            element.node.elementType == com.intellij.sql.psi.SqlTokens.SQL_STRING_TOKEN &&
            text.isNotEmpty() && text[0] !in "'\"\$UuXx"
        if (!isMaskComment && !isCollapsedString) return

        val base = element.textRange.startOffset
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    val word = text.substring(start, i)
                    val key = if (TrinoRecolorKeywords.isKeyword(word)) {
                        DefaultLanguageHighlighterColors.KEYWORD
                    } else {
                        DefaultLanguageHighlighterColors.IDENTIFIER
                    }
                    recolor(holder, base, start, i, key)
                }
                c.isDigit() -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '.')) i++
                    recolor(holder, base, start, i, DefaultLanguageHighlighterColors.NUMBER)
                }
                c == '\'' || c == '"' -> {
                    val start = i
                    i++
                    while (i < text.length && text[i] != c) i++
                    if (i < text.length) i++ // closing quote
                    val key = if (c == '\'') DefaultLanguageHighlighterColors.STRING else DefaultLanguageHighlighterColors.IDENTIFIER
                    recolor(holder, base, start, i, key)
                }
                else -> {
                    val start = i
                    i++
                    recolor(holder, base, start, i, HighlighterColors.TEXT)
                }
            }
        }
    }

    private fun recolor(holder: AnnotationHolder, base: Int, start: Int, end: Int, key: TextAttributesKey) {
        if (end <= start) return
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(base + start, base + end))
            .textAttributes(key)
            .create()
    }

    private companion object {
        /** Genuine SQL comments always start with their introducer; masked spans start with SQL. */
        private fun isRealComment(text: String): Boolean =
            text.startsWith("/*") || text.startsWith("--")
    }
}

/**
 * Compact keyword set for the masked-span recolorer — display-layer only (never parsing).
 * Deliberately small: anything missing just renders as an identifier. The SHOW FUNCTIONS-fed
 * full catalog arrives with Stage 3 metadata.
 */
object TrinoRecolorKeywords {
    private val WORDS = setOf(
        "SELECT", "FROM", "WHERE", "GROUP", "ORDER", "BY", "HAVING", "LIMIT", "OFFSET", "FETCH",
        "FIRST", "ROWS", "ONLY", "JOIN", "LEFT", "RIGHT", "FULL", "INNER", "OUTER", "CROSS", "ON",
        "USING", "AS", "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "BETWEEN", "CASE", "WHEN",
        "THEN", "ELSE", "END", "UNION", "EXCEPT", "INTERSECT", "ALL", "ANY", "SOME", "DISTINCT",
        "EXISTS", "CREATE", "TABLE", "REPLACE", "INSERT", "INTO", "VALUES", "UPDATE", "DELETE",
        "SET", "BEGIN", "DECLARE", "IF", "ELSEIF", "WHILE", "REPEAT", "UNTIL", "LOOP", "ITERATE",
        "LEAVE", "RETURN", "RETURNS", "DO", "DEFAULT", "FUNCTION", "LANGUAGE", "CAST", "TRY_CAST",
        "FOR", "TIMESTAMP", "VERSION", "OF", "AT", "LOCAL", "TIME", "ZONE", "INTERVAL", "ROW",
        "MAP", "ARRAY", "MATCH_RECOGNIZE", "PARTITION", "MEASURES", "PATTERN", "DEFINE", "AFTER",
        "MATCH", "SKIP", "PAST", "PIVOT", "OVERFLOW", "ERROR", "TRUNCATE", "WITH", "WITHOUT",
        "COUNT", "COLUMNS", "PATH", "PASSING", "RETURNING", "WRAPPER", "QUOTES", "KEY", "VALUE",
        "EMPTY", "UNKNOWN", "ABSENT", "OVER", "WINDOW", "RECURSIVE", "JSON",
    )

    fun isKeyword(word: String): Boolean = word.uppercase() in WORDS
}

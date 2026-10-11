package dev.sort.trino.pipes

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.annotation.*
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import dev.brikk.house.sql.parser.PipeStageSplitter
import dev.brikk.house.sql.parser.TokenType
import dev.brikk.house.sql.shape.ShapeCatalog
import dev.brikk.house.sql.shape.SqlFragment
import dev.sort.trino.brikk.*

class TrinoPipesCompletionContributor : CompletionContributor() {
    init { extend(CompletionType.BASIC, PlatformPatterns.psiElement(), Provider) }
    private object Provider : CompletionProvider<CompletionParameters>() {
        val keywords = listOf("WHERE", "SELECT", "EXTEND", "AGGREGATE", "ORDER BY", "LIMIT", "OFFSET", "DISTINCT",
            "JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL OUTER JOIN", "CROSS JOIN", "AS", "DROP", "SET", "RENAME",
            "UNION ALL", "UNION DISTINCT", "INTERSECT", "EXCEPT", "PIVOT", "UNPIVOT")
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val file = parameters.originalFile
            if (!TrinoPipes.isEnabled(file)) return
            val manager = InjectedLanguageManager.getInstance(file.project)
            val brikk = BrikkTrinoProfile.forFile(file) != null
            val text = if (brikk) manager.getUnescapedText(file) else file.text
            val offset = (if (brikk) manager.mapInjectedOffsetToUnescaped(file, parameters.offset) else parameters.offset).coerceIn(0, text.length)
            val chunk = TrinoPipes.chunkAt(text, offset) ?: return
            val before = chunk.text.take((offset - chunk.startOffset).coerceIn(0, chunk.text.length))
            val split = runPipeCatching { PipeStageSplitter.split(before, "trino") }.getOrNull()
            val boundary = split?.tokens?.let { tokens ->
                var depth = 0
                var last = -1
                for (token in tokens) when (token.tokenType) {
                    TokenType.L_PAREN, TokenType.L_BRACKET, TokenType.L_BRACE -> depth++
                    TokenType.R_PAREN, TokenType.R_BRACKET, TokenType.R_BRACE -> depth--
                    TokenType.PIPE_GT -> if (depth == 0) last = token.start
                    else -> {}
                }
                last
            } ?: -1
            if (boundary >= 0) {
                val partial = before.substring(boundary + 2).trimStart()
                val matching = keywords.filter { it.startsWith(partial.trimEnd(), true) }
                val complete = partial.lastOrNull()?.isWhitespace() == true && keywords.any { it.equals(partial.trimEnd(), true) }
                if (!complete && matching.isNotEmpty()) {
                    val sink = result.caseInsensitive().withPrefixMatcher(partial.trimEnd())
                    matching.forEach { sink.addElement(LookupElementBuilder.create(it).withTypeText("PIPE stage", true)) }
                    result.stopHere()
                    return
                }
            }
            if (brikk) {
                val analysis = BrikkSqlScopes.analyze(text.substring(0, offset) + "__brikk_caret" + text.substring(offset), BrikkTrinoProfile.contracts(file))
                val ref = analysis?.at(offset)
                if (ref?.kind == BrikkSqlScopes.Kind.COLUMN) {
                    val rows = if (ref.qualifier.isBlank()) ref.scope.relations else listOfNotNull(ref.scope.relation(ref.qualifier))
                    rows.filter { it.managed }.flatMap { it.columns }.distinctBy { it.name }.forEach { column ->
                        result.caseInsensitive().addElement(PrioritizedLookupElement.withPriority(
                            LookupElementBuilder.create(column.name).withTypeText(column.kotlinClassId?.substringAfterLast('.') ?: "unknown type", true)
                                .withTailText("  Kotlin relation column", true), 120.0))
                    }
                }
            } else if (boundary >= 0) {
                val input = before.substring(0, boundary).trim()
                runPipeCatching { SqlFragment(input, "trino").outputShape(ShapeCatalog.EMPTY).names() }.getOrNull().orEmpty()
                    .filter { it != "*" && !it.matches(Regex("_col_\\d+")) }.distinct().forEach {
                        result.caseInsensitive().addElement(LookupElementBuilder.create(it).withTypeText("PIPE input column", true))
                    }
            }
        }
    }
}

class TrinoPipeStageKeywordAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        if (!TrinoPipes.isEnabled(file)) return
        val manager = InjectedLanguageManager.getInstance(file.project)
        val brikk = BrikkTrinoProfile.forFile(file) != null
        val text = if (brikk) manager.getUnescapedText(file) else file.text
        for (chunk in TrinoPipes.chunks(text).filter { it.hasPipeOperator }) {
            val stages = runPipeCatching { PipeStageSplitter.split(chunk.text, "trino").stages }.getOrNull() ?: continue
            for (stage in stages) {
                val token = stage.tokens.firstOrNull() ?: continue
                val start = chunk.startOffset + token.start
                val end = chunk.startOffset + token.end + 1
                val range = if (brikk) TextRange(manager.mapUnescapedOffsetToInjected(file, start), manager.mapUnescapedOffsetToInjected(file, end)) else TextRange(start, end)
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range).textAttributes(DefaultLanguageHighlighterColors.KEYWORD).create()
            }
        }
    }
}

package dev.sort.trino.sql

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement

/**
 * Stage-1 livability baseline (doris/duckdb pattern): the PG substrate paints red
 * PsiErrorElements over Trino-only expressions it can't parse (lambdas, TVF TABLE() args,
 * MATCH_RECOGNIZE, ...). Suppress base-parser syntax errors for Trino (Brikk) files entirely —
 * DISPLAY ONLY: the census scoreboard keeps counting the real PsiErrorElements, so coverage
 * truth is never hidden from us, only from the user's editor.
 *
 * The authority that replaces this silence with REAL errors is the bundled trino-parser
 * validator annotator (the engine's own grammar, version 483); until it lands, no red is better
 * than wrong red.
 */
class TrinoHighlightErrorFilter : HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        val file = element.containingFile ?: return true
        return !file.language.isKindOf(TrinoSqlDialect.INSTANCE)
    }
}

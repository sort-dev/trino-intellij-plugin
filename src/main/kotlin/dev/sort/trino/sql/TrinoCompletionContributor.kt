package dev.sort.trino.sql

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import icons.DatabaseIcons
import javax.swing.Icon

/**
 * Trino (Brikk) bundled-catalog completion (doris/duckdb FunctionProvider pattern): every function
 * of [TrinoFunctionCatalog] offered with its kind icon and return type as the grey type text, parens
 * inserted with the caret placed between them; plus the reserved-word list as plain CAPS keywords.
 *
 * Table functions (`sequence`, `exclude_columns`) are offered everywhere too — they ARE relations
 * in a FROM `TABLE(...)` position — so no positional gating here; the platform's own SQL completion
 * still contributes schema objects alongside us.
 *
 * Icon mapping (DatabaseIcons is the SQL-object icon set the platform ships; there is no dedicated
 * window/analytic glyph, so window functions reuse [DatabaseIcons.Function]):
 *   AGGREGATE -> Aggregate   TABLE -> Table   SCALAR / WINDOW / OTHER -> Function
 */
class TrinoCompletionContributor : CompletionContributor() {

    init {
        // Scope comes from the plugin.xml registration (language="TrinoBrikk"). A withLanguage(...)
        // pattern here does NOT match the completion-position leaf and would silently disable the
        // provider (duckdb bisect). extend() with a bare psiElement() is correct.
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), CatalogProvider)
    }

    private object CatalogProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val out = result.caseInsensitive()
            for (fn in TrinoFunctionCatalog.functions) {
                out.addElement(
                    LookupElementBuilder.create(fn.name)
                        .withIcon(iconFor(fn.kind))
                        .withTypeText(fn.returnType.ifBlank { fn.kind.name.lowercase() }, true)
                        .withInsertHandler { ctx, _ ->
                            val editor = ctx.editor
                            val at = ctx.tailOffset
                            val chars = editor.document.charsSequence
                            val already = at < chars.length && chars[at] == '('
                            if (!already) {
                                editor.document.insertString(at, "()")
                                editor.caretModel.moveToOffset(at + 1)
                            }
                        },
                )
            }
            // Reserved words as plain uppercase keywords (no icon). Standard-SQL overlap with the
            // platform's own keyword completion de-dupes on the lookup string.
            for (kw in TrinoFunctionCatalog.keywords) {
                out.addElement(LookupElementBuilder.create(kw).bold())
            }
        }
    }

    companion object {
        /** Single source of truth for the kind->icon mapping (tested directly). */
        internal fun iconFor(kind: TrinoFunctionCatalog.Kind): Icon = when (kind) {
            TrinoFunctionCatalog.Kind.AGGREGATE -> DatabaseIcons.Aggregate
            TrinoFunctionCatalog.Kind.TABLE -> DatabaseIcons.Table
            // SCALAR, WINDOW, OTHER — no dedicated window/analytic icon exists in DatabaseIcons.
            else -> DatabaseIcons.Function
        }
    }
}

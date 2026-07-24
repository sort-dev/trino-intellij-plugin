package dev.sort.trino.sql

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.sort.trino.catalog.TrinoAutoIntrospect
import dev.sort.trino.catalog.TrinoDataSourceResolver
import dev.sort.trino.catalog.TrinoIntrospectNotifier
import dev.sort.trino.catalog.TrinoNamespacePath
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
        // Lazy/targeted schema-tree deepening: path-typing into an enumerated-but-childless
        // namespace kicks a one-level introspection of exactly that node (offers nothing itself).
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), QualifiedPathIntrospectProvider)
    }

    /**
     * Auto-introspection trigger: completion invoked right after a qualified db-object path
     * (`hive.<caret>` or `hive.web.<caret>`) that resolves to an enumerated-but-childless namespace
     * kicks a TARGETED one-level refresh of exactly that node + an editor banner. Offers nothing
     * itself — the platform's own SQL completion supplies the freshly-loaded catalog/schema/table
     * names once the async introspection lands (invoke completion again). See
     * [dev.sort.trino.catalog.TrinoAutoIntrospect] for the scale discipline.
     */
    private object QualifiedPathIntrospectProvider : CompletionProvider<CompletionParameters>() {
        // The dotted chain right before the caret: (optionally "double-quoted") identifier segments,
        // then a dot, optional whitespace, and the partial word being typed. Group 1 = the chain
        // WITHOUT the trailing dot (`hive` or `hive.web`). Trino delimits identifiers with double
        // quotes (not backticks), so those are the only quote char stripped.
        private val PATH_BEFORE_CARET = Regex("""([A-Za-z_"][\w"]*(?:\.[A-Za-z_"][\w"]*)*)\.\s*\w*$""")

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val file = parameters.originalFile
            if (!file.language.isKindOf(TrinoSqlDialect.INSTANCE)) return
            runCatching {
                val text = file.text
                val offset = parameters.offset.coerceAtMost(text.length)
                val tail = text.substring((offset - 200).coerceAtLeast(0), offset)
                val parts = PATH_BEFORE_CARET.find(tail)?.groupValues?.get(1)
                    ?.split('.')?.map { it.trim('"') }?.filter { it.isNotEmpty() }
                    ?: return
                if (parts.isEmpty() || parts.size > 2) return  // only catalog / catalog.schema deepen

                val local = TrinoDataSourceResolver.resolve(file) ?: return
                val facade = DbPsiFacade.getInstance(file.project)
                val dataSource = facade.findDataSource(local.uniqueId)
                    ?: facade.dataSources.firstOrNull { it.uniqueId == local.uniqueId }
                    ?: return
                // The model must come from the DbDataSource wrapper, NOT LocalDataSource.model
                // (doris round 6: the LocalDataSource's own object list is silently empty).
                val roots = dataSource.model.modelRoots.map(::DasNode).toList()
                val deepen = TrinoNamespacePath.decideDeepen(roots, parts) ?: return

                val realNode = (deepen.node as? DasNode)?.das
                TrinoAutoIntrospect.request(
                    file.project, local, deepen.level, deepen.catalog, deepen.schema, realNode,
                )
                // Banner whether THIS pass kicked the refresh or an earlier one already did (the
                // request de-dupes internally; the round-18 doris lesson is one STABLE message, so
                // it never flickers on repeated keystrokes into the same namespace).
                TrinoIntrospectNotifier.reportPending(
                    file.project, file.viewProvider.virtualFile, parts.joinToString("."),
                )
            }
        }

        /** Adapts a live [DasObject] to the pure [TrinoNamespacePath.Node] walk (carries the real node). */
        private class DasNode(val das: DasObject) : TrinoNamespacePath.Node {
            override val name: String get() = das.name
            override fun childNodes(): List<TrinoNamespacePath.Node> = children(das).map(::DasNode)
        }

        /** All loaded children of [o] regardless of kind — schemas under a catalog, tables under a
         *  schema. Unions the kind-specific views with the all-kinds view (doris belt-and-braces:
         *  getDasChildren(null) has been observed to skip a subtree in the two-level model). */
        private fun children(o: DasObject): List<DasObject> =
            (o.getDasChildren(ObjectKind.SCHEMA).toList() +
                o.getDasChildren(ObjectKind.TABLE).toList() +
                o.getDasChildren(null).toList()).distinct()
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

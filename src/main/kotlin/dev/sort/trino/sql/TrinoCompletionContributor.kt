package dev.sort.trino.sql

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.database.model.DasObject
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbPsiFacade
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.brikk.house.sql.intellij.recoverEditorOperation
import dev.sort.trino.catalog.TrinoAutoIntrospect
import dev.sort.trino.catalog.TrinoDataSourceResolver
import dev.sort.trino.catalog.TrinoNamespacePath

class TrinoCompletionContributor : CompletionContributor() {

    init {
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
            if (!file.language.isKindOf(TrinoSqlDialect)) return
            recoverEditorOperation {
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
                    file.viewProvider.virtualFile,
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
}

package dev.sort.trino.brikk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.DasTable
import com.intellij.database.psi.DbElement
import com.intellij.database.util.DasUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.inspections.suppression.SqlInspectionSuppressorDelegate
import com.intellij.sql.psi.SqlAsExpression
import com.intellij.sql.psi.SqlQueryExpression
import com.intellij.sql.psi.SqlReferenceExpression
import dev.brikk.house.sql.intellij.catalog.DasRelationFacts
import dev.brikk.house.sql.intellij.relation.*
import dev.brikk.house.sql.scope.RelationFacts
import dev.brikk.house.sql.scope.RelationScope
import dev.sort.trino.catalog.trinoDataSourceResolver
import dev.sort.trino.sql.TrinoSqlDialect

object TrinoRelationProfile : RelationScopeProfile {
    val externalFacts = ExtensionPointName.create<ExternalRelationFacts>("dev.sort.trino-intellij-plugin.relationFacts")
    private val physical = DasRelationFacts(trinoDataSourceResolver)
    override val language get() = TrinoSqlDialect
    override val dialect = "trino"
    override val providers get() = externalFacts.extensionList + physical
    override val hostSql get() = TrinoHostSql
}

class TrinoRelationResolveExtension : ExternalRelationResolveExtension(TrinoRelationProfile)
class TrinoRelationInspectionSuppressor : SqlInspectionSuppressorDelegate {
    private val shared = object : ExternalRelationInspectionSuppressor(TrinoRelationProfile) {}

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        val file = element.containingFile ?: return false
        if (!file.language.isKindOf(TrinoSqlDialect)) return false
        return shared.isSuppressedFor(element, toolId) ||
            TrinoHostSql.marker(file) == null && toolId.removeSuffix("Inspection") == "SqlResolve" &&
                (boundUnaliasedQualifier(element) || unknownRecordQualifier(element) || opaqueUnknownColumn(element))
    }

    // 263 may report a table-name qualifier even when its FROM source binds it. Limit this shim
    // to the qualifier's own name; missing tables, columns and unrelated qualifiers stay reported.
    private fun boundUnaliasedQualifier(element: PsiElement): Boolean {
        val qualifier = PsiTreeUtil.getNonStrictParentOfType(element, SqlReferenceExpression::class.java) ?: return false
        val column = qualifier.parent as? SqlReferenceExpression ?: return false
        if (column.qualifierExpression !== qualifier || column.referenceElementType.targetKind != ObjectKind.COLUMN) return false
        if (element !== qualifier && element.textRange != qualifier.identifier?.textRange) return false
        val query = PsiTreeUtil.getParentOfType(column, SqlQueryExpression::class.java) ?: return false
        val from = query.tableExpression?.fromClause ?: return false
        return PsiTreeUtil.findChildrenOfType(from, SqlReferenceExpression::class.java).any { table ->
            table.referenceElementType.targetKind == ObjectKind.TABLE && table.parent !is SqlReferenceExpression &&
                table.parent !is SqlAsExpression && PsiTreeUtil.getParentOfType(table, SqlQueryExpression::class.java) === query &&
                table.name.equals(qualifier.name, ignoreCase = true)
        }
    }

    // A dereference can name a ROW-valued column, not a table alias. When none of the query's
    // source columns are known, the platform cannot prove that qualifier absent either.
    private fun unknownRecordQualifier(element: PsiElement): Boolean {
        val qualifier = PsiTreeUtil.getNonStrictParentOfType(element, SqlReferenceExpression::class.java) ?: return false
        val column = qualifier.parent as? SqlReferenceExpression ?: return false
        if (qualifier.qualifierExpression != null || column.qualifierExpression !== qualifier ||
            column.referenceElementType.targetKind != ObjectKind.COLUMN) return false
        if (element !== qualifier && element.textRange != qualifier.identifier?.textRange) return false
        val query = PsiTreeUtil.getParentOfType(column, SqlQueryExpression::class.java) ?: return false
        val from = query.tableExpression?.fromClause ?: return false
        val tables = PsiTreeUtil.findChildrenOfType(from, SqlReferenceExpression::class.java).filter {
            it.referenceElementType.targetKind == ObjectKind.TABLE && it.parent !is SqlReferenceExpression &&
                PsiTreeUtil.getParentOfType(it, SqlQueryExpression::class.java) === query
        }
        return tables.isNotEmpty() && tables.all { table ->
            val resolved = table.resolve()
            val model = (resolved as? DasTable) ?: (resolved as? DbElement)?.delegate as? DasTable
            resolved == null || model != null && DasUtil.getColumns(model).isEmpty
        }
    }

    // A legal native statement can exceed core's editor grammar. In that case keep the
    // platform's table reports, but do not invent column knowledge from unresolved base tables.
    private fun opaqueUnknownColumn(element: PsiElement): Boolean {
        val reference = PsiTreeUtil.getNonStrictParentOfType(element, SqlReferenceExpression::class.java) ?: return false
        if (reference.referenceElementType.targetKind != ObjectKind.COLUMN) return false
        if (element !== reference && element.textRange != reference.identifier?.textRange) return false
        val file = reference.containingFile
        val opaque = com.intellij.psi.util.CachedValuesManager.getCachedValue(file) {
            com.intellij.psi.util.CachedValueProvider.Result.create(
                RelationScope.analyze(file.text, "trino", RelationFacts()).references.isEmpty(), file)
        }
        if (!opaque) return false
        val tables = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java).filter {
            it.referenceElementType.targetKind == ObjectKind.TABLE && it.parent !is SqlReferenceExpression &&
                PsiTreeUtil.getParentOfType(it, com.intellij.sql.psi.SqlFromClause::class.java) != null
        }
        if (tables.isEmpty()) return false
        var unknown = false
        for (table in tables) {
            val resolved = table.resolve()
            val model = (resolved as? DasTable) ?: (resolved as? DbElement)?.delegate as? DasTable
            if (model != null && DasUtil.getColumns(model).isNotEmpty) return false
            if (resolved == null || model != null) unknown = true
        }
        return unknown
    }
}
class TrinoRelationCompletionContributor : ExternalRelationCompletionContributor(TrinoRelationProfile)
class TrinoRelationGotoDeclarationHandler : ExternalRelationGotoDeclarationHandler(TrinoRelationProfile)
class TrinoRelationAnnotator : ExternalRelationAnnotator(TrinoRelationProfile)

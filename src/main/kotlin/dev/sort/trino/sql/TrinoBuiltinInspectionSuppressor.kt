package dev.sort.trino.sql

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.inspections.suppression.SqlInspectionSuppressorDelegate
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.sql.psi.SqlCompositeElementTypes

/** Stock PostgreSQL function lookup does not know the Trino metadata catalog. */
class TrinoBuiltinInspectionSuppressor : SqlInspectionSuppressorDelegate {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId.removeSuffix("Inspection") != "SqlResolve") return false
        val file = element.containingFile ?: return false
        if (!file.language.isKindOf(TrinoSqlDialect)) return false
        val type = PsiTreeUtil.getNonStrictParentOfType(element, SqlReferenceExpression::class.java)
        if (type?.referenceElementType?.targetKind?.name() == "OBJECT TYPE" && type.qualifierExpression == null &&
            (element === type || element.textRange == type.identifier?.textRange) &&
            type.name?.let(::knownNativeType) == true) return true
        val call = PsiTreeUtil.getNonStrictParentOfType(element, SqlFunctionCallExpression::class.java) ?: return false
        if (tableWrapperBuiltin(call, element)) return true
        val name = call.nameElement?.identifier ?: return false
        if (element !== call && element.textRange != name.textRange && element !== call.nameElement) return false
        // Qualified calls may name user functions, not builtins with a matching final component.
        if (call.nameElement?.qualifierExpression != null) return false
        return TrinoCompletionProfile.functions(file).any { it.name.equals(name.text.trim('"'), ignoreCase = true) }
    }

    private fun tableWrapperBuiltin(call: SqlFunctionCallExpression, element: PsiElement): Boolean {
        val name = call.nameElement ?: return false
        val wrapper = if (name.name.equals("TABLE", ignoreCase = true)) call
            else PsiTreeUtil.getParentOfType(call, SqlFunctionCallExpression::class.java) ?: return false
        if (!wrapper.nameElement?.name.equals("TABLE", ignoreCase = true) ||
            wrapper.parent?.node?.elementType != SqlCompositeElementTypes.SQL_TABLE_PROCEDURE_CALL_EXPRESSION) return false
        val inner = wrapper.parameterList?.expressionList?.singleOrNull() as? SqlFunctionCallExpression ?: return false
        val fullName = inner.nameElement?.text ?: return false
        val known = TrinoCompletionProfile.snapshot(call.containingFile)?.tableFunctions.orEmpty().any {
            it.queryable && (it.name.equals(fullName, ignoreCase = true) || "system.${it.name}".equals(fullName, ignoreCase = true))
        } || TrinoCompletionProfile.functions(call.containingFile).any {
            it.kind == dev.brikk.house.sql.metadata.FunctionKind.TABLE_VALUED &&
                (it.name.equals(fullName, ignoreCase = true) || "system.${it.name}".equals(fullName, ignoreCase = true))
        }
        return known && (element === call || name.textRange.contains(element.textRange))
    }

    private companion object {
        val TYPES = setOf("tinyint", "smallint", "integer", "int", "bigint", "real", "double", "decimal", "boolean",
            "json", "varbinary", "varchar", "char", "array", "map", "row", "date", "time", "timestamp", "interval",
            "with", "without", "zone", "precision")

        fun knownNativeType(name: String): Boolean {
            if (!name.matches(Regex("[A-Za-z0-9_(),\\s]+"))) return false
            val words = Regex("[A-Za-z_]+").findAll(name.lowercase(java.util.Locale.ROOT)).map { it.value }.toList()
            return words.isNotEmpty() && words.first() !in setOf("with", "without", "zone", "precision") && words.all { it in TYPES }
        }
    }
}

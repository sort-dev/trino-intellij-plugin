package dev.sort.trino.brikk

import com.intellij.lang.annotation.*
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.sql.inspections.suppression.SqlInspectionSuppressorDelegate
import dev.brikk.house.intellij.ShapeKnowledge

class BrikkSqlInspectionSuppressor : SqlInspectionSuppressorDelegate {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId.removeSuffix("Inspection") !in setOf("SqlResolve", "SqlType", "SqlSignature")) return false
        val ref = BrikkSqlEditorSupport.reference(element) ?: return false
        return when (ref.kind) {
            BrikkSqlScopes.Kind.SLOT -> true
            BrikkSqlScopes.Kind.QUALIFIER -> ref.scope.relation(ref.name)?.managed == true
            BrikkSqlScopes.Kind.COLUMN -> if (ref.qualifier.isNotBlank()) ref.scope.relation(ref.qualifier)?.managed == true
                else ref.scope.relations.isNotEmpty() && ref.scope.relations.all { it.managed }
        }
    }
}

class BrikkSqlContractAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        val analysis = BrikkSqlEditorSupport.analysis(file) ?: return
        val manager = InjectedLanguageManager.getInstance(file.project)
        for (ref in analysis.references) {
            if (ref.kind != BrikkSqlScopes.Kind.COLUMN || ref.name == "*") continue
            val rows = if (ref.qualifier.isNotBlank()) listOfNotNull(ref.scope.relation(ref.qualifier)) else ref.scope.relations
            if (rows.isEmpty() || rows.any { !it.managed || !it.available }) continue
            val matches = ref.scope.candidates(ref.name, ref.qualifier)
            val partial = rows.any { it.knowledge == ShapeKnowledge.PARTIAL }
            val message = when {
                matches.size > 1 -> "Ambiguous relation column '${ref.name}'; qualify the slot alias."
                matches.isNotEmpty() -> null
                partial -> "Column '${ref.name}' is not guaranteed by this partial relation contract."
                else -> "Column '${ref.name}' is not present in this full relation contract."
            } ?: continue
            val range = TextRange(manager.mapUnescapedOffsetToInjected(file, ref.start), manager.mapUnescapedOffsetToInjected(file, ref.end))
            val editable = manager.intersectWithAllEditableFragments(file, range).firstOrNull() ?: continue
            holder.newAnnotation(if (matches.isEmpty() && partial) HighlightSeverity.WARNING else HighlightSeverity.ERROR, message).range(editable).create()
        }
    }
}

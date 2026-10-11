package dev.sort.trino.brikk

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import dev.brikk.house.intellij.NavigationKind

class BrikkSqlGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getActionText(context: DataContext): String? {
        val editor = CommonDataKeys.EDITOR.getData(context) ?: return null
        val file = CommonDataKeys.PSI_FILE.getData(context) ?: return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        val ref = BrikkSqlEditorSupport.reference(element, editor.caretModel.offset) ?: return null
        val targets = ref.scope.candidates(ref.name, ref.qualifier).mapNotNull { it.second.navigation }
        return if (targets.isNotEmpty() && targets.all { it.kind == NavigationKind.RELATION_FALLBACK })
            "Go to Kotlin relation contract (generated column origin unavailable)" else null
    }
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val ref = BrikkSqlEditorSupport.reference(element, offset) ?: return null
        val targets = if (ref.kind == BrikkSqlScopes.Kind.COLUMN) ref.scope.candidates(ref.name, ref.qualifier).mapNotNull { (row, column) ->
            if (row.managed) BrikkSqlEditorSupport.navigation(element.containingFile, column.navigation) else null
        } else ref.scope.relation(ref.name)?.let { listOfNotNull(BrikkSqlEditorSupport.navigation(element.containingFile, it.navigation)) }.orEmpty()
        return targets.distinct().takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}

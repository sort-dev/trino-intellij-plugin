package dev.sort.trino.brikk

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import dev.brikk.house.intellij.NavigationTarget

internal object BrikkSqlEditorSupport {
    fun analysis(file: PsiFile): BrikkSqlScopes.Analysis? {
        if (BrikkTrinoProfile.forFile(file) == null) return null
        return BrikkSqlScopes.analyze(InjectedLanguageManager.getInstance(file.project).getUnescapedText(file), BrikkTrinoProfile.contracts(file))
    }
    fun reference(element: PsiElement, offset: Int = element.textRange.startOffset): BrikkSqlScopes.Reference? {
        val file = element.containingFile ?: return null
        return analysis(file)?.at(InjectedLanguageManager.getInstance(file.project).mapInjectedOffsetToUnescaped(file, offset))
    }
    fun navigation(file: PsiFile, target: NavigationTarget?): PsiElement? {
        val location = target?.location ?: return null
        val vf = VirtualFileManager.getInstance().findFileByUrl(location.fileUrl) ?: return null
        val psi = PsiManager.getInstance(file.project).findFile(vf) ?: return null
        val leaf = psi.findElementAt(location.start) ?: return null
        return generateSequence(leaf) { it.parent }.firstOrNull { it is PsiNamedElement } ?: leaf
    }
}

package dev.sort.trino.brikk

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile

/** Kotlin-free copied editing facts. SQL inspection text never contains runtime values. */
data class BrikkTrinoProfile(val binds: List<String>, val slots: List<String>, val issues: List<String>) {
    companion object {
        val KEY: Key<BrikkTrinoProfile> = Key.create("dev.sort.trino.brikk.profile")
        fun forFile(file: PsiFile?): BrikkTrinoProfile? = file?.getUserData(KEY)
            ?: file?.let { InjectedLanguageManager.getInstance(it.project).getInjectionHost(it)?.getUserData(KEY) }
    }
}

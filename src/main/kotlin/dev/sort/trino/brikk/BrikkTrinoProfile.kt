package dev.sort.trino.brikk

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import dev.brikk.house.intellij.ShapeResult

/** Kotlin-free copied editing facts. SQL inspection text never contains runtime values. */
data class BrikkTrinoProfile(val binds: List<String>, val slots: List<String>, val issues: List<String>) {
    companion object {
        val KEY: Key<BrikkTrinoProfile> = Key.create("dev.sort.trino.brikk.profile")
        val RELATIONS: Key<() -> Map<String, ShapeResult>> = Key.create("dev.sort.trino.brikk.relations")
        fun forFile(file: PsiFile?): BrikkTrinoProfile? = file?.getUserData(KEY)
            ?: file?.let { InjectedLanguageManager.getInstance(it.project).getInjectionHost(it)?.getUserData(KEY) }
        fun contracts(file: PsiFile): Map<String, ShapeResult> {
            val profile = forFile(file) ?: return emptyMap()
            val host = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)
            val contracts = host?.getUserData(RELATIONS)?.invoke().orEmpty().toMutableMap()
            profile.slots.forEach { contracts.putIfAbsent(it, ShapeResult.Unavailable("Relation analysis is pending")) }
            return contracts
        }
    }
}

internal fun isBrikkInjectedSql(file: PsiFile): Boolean = BrikkTrinoProfile.forFile(file) != null &&
    InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)

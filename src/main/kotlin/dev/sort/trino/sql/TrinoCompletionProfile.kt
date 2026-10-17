package dev.sort.trino.sql

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.intellij.completion.KeywordScope
import dev.brikk.house.sql.intellij.completion.SqlCompletionContributor
import dev.brikk.house.sql.intellij.completion.SqlCompletionProfile
import dev.brikk.house.sql.intellij.completion.SqlLookupKind
import dev.brikk.house.sql.intellij.completion.SqlLookupStyles
import dev.brikk.house.sql.metadata.TRINO_METADATA
import dev.sort.trino.catalog.TrinoLiveCatalogService

object TrinoCompletionProfile : SqlCompletionProfile {
    override val language get() = TrinoSqlDialect
    override val metadata = TRINO_METADATA
    private fun completion(file: PsiFile) = ApplicationManager.getApplication().service<TrinoLiveCatalogService>().completion(file)
    override fun snapshot(file: PsiFile) = completion(file).snapshot
    override fun functions(file: PsiFile) = completion(file).functions
    override fun serverOnlyFunctions(file: PsiFile) = completion(file).serverOnlyFunctions
    override val keywords = KeywordScope.RESERVED
    override val styles = SqlLookupStyles.DEFAULT.let { defaults ->
        defaults.with(
            *listOf(
                SqlLookupKind.FUNCTION to "scalar",
                SqlLookupKind.AGGREGATE to "aggregate",
                SqlLookupKind.WINDOW to "window",
                SqlLookupKind.TABLE_FUNCTION to "table",
            ).map { (kind, label) ->
                kind to defaults[kind].copy(typeText = label,
                    showsSignature = false, showsReturnType = true)
            }.toTypedArray(),
        )
    }
}

class TrinoMetadataCompletionContributor : SqlCompletionContributor(TrinoCompletionProfile)

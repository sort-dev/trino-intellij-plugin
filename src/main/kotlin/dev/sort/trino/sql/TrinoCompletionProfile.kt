package dev.sort.trino.sql

import dev.brikk.house.sql.intellij.completion.KeywordScope
import dev.brikk.house.sql.intellij.completion.LookupCase
import dev.brikk.house.sql.intellij.completion.SqlCompletionContributor
import dev.brikk.house.sql.intellij.completion.SqlCompletionProfile
import dev.brikk.house.sql.intellij.completion.SqlLookupKind
import dev.brikk.house.sql.intellij.completion.SqlLookupStyles
import dev.brikk.house.sql.metadata.TRINO_METADATA
import icons.DatabaseIcons

object TrinoCompletionProfile : SqlCompletionProfile {
    override val language get() = TrinoSqlDialect.INSTANCE
    override val metadata = TRINO_METADATA
    override val keywords = KeywordScope.RESERVED
    override val styles = SqlLookupStyles.DEFAULT.let { defaults ->
        defaults.with(
            *listOf(
                SqlLookupKind.FUNCTION to "scalar",
                SqlLookupKind.AGGREGATE to "aggregate",
                SqlLookupKind.WINDOW to "window",
                SqlLookupKind.TABLE_FUNCTION to "table",
            ).map { (kind, label) ->
                kind to defaults[kind].copy(
                    icon = if (kind == SqlLookupKind.WINDOW) DatabaseIcons.Function else defaults[kind].icon,
                    typeText = label, case = LookupCase.AS_GIVEN,
                    showsSignature = false, showsReturnType = true)
            }.toTypedArray(),
            SqlLookupKind.KEYWORD to defaults[SqlLookupKind.KEYWORD].copy(case = LookupCase.AS_GIVEN),
        )
    }
}

class TrinoMetadataCompletionContributor : SqlCompletionContributor(TrinoCompletionProfile)

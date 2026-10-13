package dev.sort.trino.brikk

import com.intellij.openapi.extensions.ExtensionPointName
import dev.brikk.house.sql.intellij.catalog.DasRelationFacts
import dev.brikk.house.sql.intellij.relation.*
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
class TrinoRelationInspectionSuppressor : ExternalRelationInspectionSuppressor(TrinoRelationProfile)
class TrinoRelationCompletionContributor : ExternalRelationCompletionContributor(TrinoRelationProfile)
class TrinoRelationGotoDeclarationHandler : ExternalRelationGotoDeclarationHandler(TrinoRelationProfile)
class TrinoRelationAnnotator : ExternalRelationAnnotator(TrinoRelationProfile)

package dev.sort.trino.catalog

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.brikk.house.sql.intellij.catalog.AutoIntrospectService
import dev.brikk.house.sql.intellij.catalog.IntrospectBannerProvider
import dev.brikk.house.sql.intellij.catalog.QualifiedPathIntrospectContributor
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class TrinoAutoIntrospectService(project: Project, scope: CoroutineScope) : AutoIntrospectService(project, scope) {
    override val profile = TrinoCatalogProfile
}

class TrinoIntrospectBannerProvider : IntrospectBannerProvider(TrinoAutoIntrospectService::class.java)

class TrinoQualifiedPathIntrospectContributor : QualifiedPathIntrospectContributor(TrinoAutoIntrospectService::class.java)

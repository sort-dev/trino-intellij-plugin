package dev.sort.trino.catalog

import com.intellij.openapi.components.Service
import dev.brikk.house.sql.intellij.catalog.HarvestConnectionInterceptor
import dev.brikk.house.sql.intellij.catalog.LiveCatalogCleanup
import dev.brikk.house.sql.intellij.catalog.LiveCatalogService
import dev.brikk.house.sql.intellij.catalog.TrinoLiveCatalogProfile
import dev.sort.trino.TrinoDbms
import kotlinx.coroutines.CoroutineScope

object TrinoFunctionCatalogProfile : TrinoLiveCatalogProfile {
    override val dbms = TrinoDbms.TRINO_BRIKK
    override val enabled = true
}

@Service(Service.Level.APP)
class TrinoLiveCatalogService(scope: CoroutineScope) : LiveCatalogService(scope) {
    override val profile = TrinoFunctionCatalogProfile
}

class TrinoHarvestConnectionInterceptor : HarvestConnectionInterceptor(TrinoLiveCatalogService::class.java)

class TrinoLiveCatalogCleanup : LiveCatalogCleanup(TrinoLiveCatalogService::class.java)

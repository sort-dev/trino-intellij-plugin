package dev.sort.trino.catalog

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.brikk.house.sql.intellij.catalog.TextExplainConfiguration
import dev.brikk.house.sql.intellij.catalog.TextExplainProfile
import dev.brikk.house.sql.intellij.catalog.TextExplainService
import dev.sort.trino.TrinoDbms
import dev.sort.trino.TRINO_SEARCH_PATH
import dev.sort.trino.pipes.TrinoPipeDialect
import kotlinx.coroutines.CoroutineScope

object TrinoTextExplainProfile : TextExplainProfile {
    override val dbms = TrinoDbms.TRINO_BRIKK
    override val productLabel = "Trino"
    override val dialect = "trino"
    override val enabled = true
    override val searchPath = TRINO_SEARCH_PATH
    override val dataSources = trinoDataSourceResolver
    override val pipe = TrinoPipeDialect
}

@Service(Service.Level.PROJECT)
open class TrinoTextExplainService(project: Project, scope: CoroutineScope) : TextExplainService(project, scope) {
    override val profile = TrinoTextExplainProfile
}

class TrinoTextExplainConfiguration : TextExplainConfiguration(TrinoTextExplainService::class.java)

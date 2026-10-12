package dev.sort.trino.pipes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.brikk.house.sql.intellij.ToggleConfigurable
import dev.brikk.house.sql.intellij.ToggleSettings
import dev.sort.trino.sql.TrinoSqlDialect

@Service(Service.Level.PROJECT)
@State(name = "TrinoPipesSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class TrinoPipesSettings(project: Project) : ToggleSettings(project, TrinoSqlDialect.INSTANCE)

class TrinoPipesConfigurable(private val project: Project) : ToggleConfigurable(
    "dev.sort.trino.pipes", TrinoPipeDialect.label, "Enable PIPE queries in this project", vetoProperty = TrinoPipeDialect.vetoProperty,
) {
    override val settings get() = project.service<TrinoPipesSettings>()
}

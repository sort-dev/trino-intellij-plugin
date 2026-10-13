package dev.sort.trino.pipes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.brikk.house.sql.intellij.ToggleConfigurable
import dev.brikk.house.sql.intellij.ToggleSettings
import dev.sort.trino.sql.TrinoSqlDialect

@Service(Service.Level.PROJECT)
@State(name = "TrinoPipesSettings", storages = [Storage(ToggleSettings.STORAGE)])
class TrinoPipesSettings(project: Project) : ToggleSettings(project, TrinoSqlDialect) {
    private val revision = java.util.concurrent.atomic.AtomicLong()
    val diagnosticRevision get() = revision.get()
    override fun onChanged(enabled: Boolean) { revision.incrementAndGet() }
}

class TrinoPipesConfigurable(private val project: Project) : ToggleConfigurable(
    "dev.sort.trino.pipes", TrinoPipeDialect.label, "Trino PIPE support",
    help = "Enable queries supported by Trino PIPE syntax in this project.", vetoProperty = TrinoPipeDialect.vetoProperty,
) {
    override val settings get() = project.service<TrinoPipesSettings>()
}

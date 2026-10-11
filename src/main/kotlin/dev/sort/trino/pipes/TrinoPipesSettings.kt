package dev.sort.trino.pipes

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.util.xmlb.annotations.OptionTag
import dev.sort.trino.sql.TrinoSqlDialect
import javax.swing.JCheckBox
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
@State(name = "TrinoPipesSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class TrinoPipesSettings(private val project: Project) : SerializablePersistentStateComponent<TrinoPipesSettings.Options>(Options()) {
    data class Options(@field:OptionTag("enabled") @JvmField val enabled: Boolean = false)
    @Volatile private var initialized = false
    var enabled: Boolean
        get() = state.enabled
        set(value) {
            if (value == state.enabled) return
            updateState { it.copy(enabled = value) }
            refreshFiles()
        }
    override fun initializeComponent() { initialized = true }
    override fun loadState(state: Options) {
        val changed = this.state.enabled != state.enabled
        super.loadState(state)
        if (initialized && changed) refreshFiles()
    }
    private fun refreshFiles() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val psi = PsiManagerEx.getInstanceEx(project)
            val files = (psi.fileManager.allCachedFiles.map { it.virtualFile } + FileEditorManager.getInstance(project).openFiles)
                .filter { it.isValid && psi.findFile(it)?.language?.isKindOf(TrinoSqlDialect.INSTANCE) == true }.distinct()
            documents.reparseFiles(files, false)
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}

class TrinoPipesConfigurable(private val project: Project) : Configurable {
    private var checkbox: JCheckBox? = null
    override fun getDisplayName() = "Trino PIPE"
    override fun createComponent(): JPanel = JPanel(java.awt.BorderLayout()).apply {
        checkbox = JCheckBox("Enable PIPE queries in this project").also { add(it, java.awt.BorderLayout.NORTH) }
        reset()
    }
    override fun isModified() = checkbox?.isSelected != project.service<TrinoPipesSettings>().enabled
    override fun apply() { checkbox?.let { project.service<TrinoPipesSettings>().enabled = it.isSelected } }
    override fun reset() { checkbox?.isSelected = project.service<TrinoPipesSettings>().enabled }
    override fun disposeUIResources() { checkbox = null }
}

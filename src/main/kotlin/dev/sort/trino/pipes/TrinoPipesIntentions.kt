package dev.sort.trino.pipes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.database.console.JdbcConsoleProvider
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.intellij.selectionExecuteOption
import dev.sort.trino.TrinoDbms
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.sql.TrinoSqlDialect
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

internal object TrinoPipesUi {
    fun chunk(file: PsiFile, editor: Editor): TrinoPipes.Chunk? {
        if (!file.language.isKindOf(TrinoSqlDialect.INSTANCE) || !TrinoPipes.isEnabled(file) || BrikkTrinoProfile.forFile(file) != null) return null
        return TrinoPipes.chunkAt(editor.document.text, editor.caretModel.offset)?.takeIf { it.hasPipeOperator }
    }
    fun preview(project: Project, file: PsiFile, editor: Editor) {
        val text = chunk(file, editor)?.text ?: return
        val result = TrinoPipesEngine.transpile(text)
        val sql = (result as? TrinoPipesEngine.Transpile.Ok)?.sql.orEmpty()
        val error = when (result) {
            is TrinoPipesEngine.Transpile.Ok -> result.executionError?.message
            is TrinoPipesEngine.Transpile.Err -> result.message
            else -> "Not a PIPE statement"
        }
        val area = JTextArea(sql).apply { isEditable = false; font = java.awt.Font(java.awt.Font.MONOSPACED, 0, 13) }
        val panel = JPanel(BorderLayout()).apply {
            add(JScrollPane(area).apply { preferredSize = Dimension(640, 360) })
            error?.let { add(JScrollPane(JTextArea(it).apply { isEditable = false; lineWrap = true; wrapStyleWord = true })
                .apply { preferredSize = Dimension(640, 100) }, BorderLayout.NORTH) }
        }
        JBPopupFactory.getInstance().createComponentPopupBuilder(panel, area)
            .setTitle(if (error == null) "Generated Trino SQL" else "Trino PIPE: execution blocked")
            .setResizable(true).setMovable(true).setRequestFocus(true).createPopup().showInBestPositionFor(editor)
    }
    fun runToStage(project: Project, file: PsiFile, editor: Editor) {
        val chunk = chunk(file, editor) ?: return
        val console = JdbcConsoleProvider.getValidConsole(project, file.viewProvider.virtualFile) ?: return
        if (console.session.connectionPoint.dbms !== TrinoDbms.TRINO_BRIKK) return
        chunk.boundaryError?.let { notify(project, it); return }
        val prefix = TrinoPipesEngine.stagePrefixAt(chunk.text, editor.caretModel.offset - chunk.startOffset)
            ?: return notify(project, "Could not identify the top-level PIPE stage.")
        val range = TextRange(chunk.startOffset + chunk.text.length - chunk.text.trimStart().length, chunk.startOffset + prefix.text.length)
        executePipeModel(console, editor, console.scriptModel.subModel(range), selectionExecuteOption(), prefix.stage == 1)
    }
}

abstract class TrinoPipeIntention(private val run: Boolean) : IntentionAction {
    override fun getText() = if (run) "Trino PIPE: run stages up to caret" else "Trino PIPE: preview generated SQL"
    override fun getFamilyName() = "Trino PIPE"
    override fun startInWriteAction() = false
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = editor != null && file != null && TrinoPipesUi.chunk(file, editor) != null
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        if (run) TrinoPipesUi.runToStage(project, file, editor) else TrinoPipesUi.preview(project, file, editor)
    }
}
class PreviewPipeSqlIntention : TrinoPipeIntention(false)
class RunPipesToStageIntention : TrinoPipeIntention(true)

abstract class TrinoPipesMenuAction(private val run: Boolean) : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = file != null && editor != null && TrinoPipesUi.chunk(file, editor) != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        if (run) TrinoPipesUi.runToStage(project, file, editor) else TrinoPipesUi.preview(project, file, editor)
    }
}
class PreviewPipeSqlAction : TrinoPipesMenuAction(false)
class RunPipesToStageAction : TrinoPipesMenuAction(true)

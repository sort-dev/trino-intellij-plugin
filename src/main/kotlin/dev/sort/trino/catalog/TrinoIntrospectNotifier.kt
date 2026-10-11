package dev.sort.trino.catalog

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.openapi.components.service
import java.util.function.Function

/**
 * A dismissible banner at the top of a Trino (Brikk) editor while a lazy introspection is in
 * flight — the async model refresh kicked by [TrinoAutoIntrospect] lands after the current
 * completion pass, so without this the user's first Tab into `hive.` seems to do nothing. The
 * banner says to invoke completion again once the namespace finishes loading (ported from the doris
 * plugin's `DorisPipesNotificationProvider`).
 *
 * Cleared when the requesting refresh finishes or the user dismisses it.
 */
class TrinoIntrospectNotifier : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out EditorNotificationPanel?>? {
        val requests = project.service<TrinoIntrospectionService>()
        val namespace = requests.pending(file) ?: return null
        return Function { _: FileEditor ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                text = "Trino: introspecting '$namespace'… invoke completion again when it finishes " +
                    "(if nothing appears, introspect it in the Database view)."
                createActionLabel("Dismiss") {
                    requests.dismiss(file)
                }
            }
        }
    }

}

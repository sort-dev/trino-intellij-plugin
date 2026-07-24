package dev.sort.trino.catalog

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.Collections
import java.util.function.Function

/**
 * A dismissible banner at the top of a Trino (Brikk) editor while a lazy introspection is in
 * flight — the async model refresh kicked by [TrinoAutoIntrospect] lands after the current
 * completion pass, so without this the user's first Tab into `hive.` seems to do nothing. The
 * banner says to invoke completion again once the namespace finishes loading (ported from the doris
 * plugin's `DorisPipesNotificationProvider`).
 *
 * Recorded on a deepen ([reportPending]); cleared on dismiss. One stable message per file (round-18
 * doris lesson: alternating texts flickered the banner on every keystroke).
 */
class TrinoIntrospectNotifier : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out EditorNotificationPanel?>? {
        val message = PENDING[file.url] ?: return null
        return Function { _: FileEditor ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                text = message
                createActionLabel("Dismiss") {
                    PENDING.remove(file.url)
                    EditorNotifications.getInstance(project).updateNotifications(file)
                }
            }
        }
    }

    companion object {
        private val PENDING: MutableMap<String, String> = Collections.synchronizedMap(HashMap())

        /** Show/refresh the "introspecting '<fqn>'…" banner for [file]. */
        fun reportPending(project: Project, file: VirtualFile, fqn: String) {
            val message = "Trino: introspecting '$fqn'… invoke completion again when it finishes " +
                "(if nothing appears, introspect it in the Database view)."
            if (PENDING.put(file.url, message) != message) {
                EditorNotifications.getInstance(project).updateNotifications(file)
            }
        }

        /** Clear the banner for [file] (e.g. once names resolve). */
        fun clearPending(project: Project, file: VirtualFile) {
            if (PENDING.remove(file.url) != null) {
                EditorNotifications.getInstance(project).updateNotifications(file)
            }
        }
    }
}

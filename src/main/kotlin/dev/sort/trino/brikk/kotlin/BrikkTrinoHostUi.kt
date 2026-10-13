package dev.sort.trino.brikk.kotlin

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import dev.brikk.house.intellij.*
import dev.brikk.house.sql.intellij.completion.SqlLookupIcons
import dev.brikk.house.sql.intellij.host.*
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.brikk.TrinoHostSql

internal data class BrikkTrinoClaimData(val profile: BrikkTrinoProfile, val pendingCause: BrikkPendingCause?)

internal fun pendingTooltip(cause: BrikkPendingCause, reason: String): String = when (cause) {
    BrikkPendingCause.INDEXING -> "Trino Brikk: waiting for indexing. Inspection SQL is not executable."
    BrikkPendingCause.ANALYSIS_PROHIBITED -> "Trino Brikk: waiting for Kotlin analysis. Inspection SQL is not executable."
    BrikkPendingCause.UNRESOLVED -> "Trino Brikk: unresolved Kotlin references. $reason"
    BrikkPendingCause.RUNTIME_ENTRY_MISSING -> "Trino Brikk: update the Brikk runtime. $reason"
    BrikkPendingCause.GENERATED_SHAPES -> "Trino Brikk: waiting for generated relation shapes. $reason"
    BrikkPendingCause.COMPILER_PLUGIN_MISSING -> "Trino Brikk: enable the Brikk compiler plugin. $reason"
}

class BrikkTrinoLineMarkerProvider : HostSqlLineMarkerProvider(TrinoHostSql, "Trino Brikk SQL", SqlLookupIcons.BrikkColumn) {
    public override fun tooltip(state: HostSqlState): String = when (val claim = state.claim) {
        is HostSqlClaim.Unavailable -> claim.reason
        is HostSqlClaim.Injected -> (claim.data as? BrikkTrinoClaimData)?.pendingCause?.let { pendingTooltip(it, "") }
            ?: "Trino Brikk: PIPE editing enabled; inspection SQL is not executable."
        HostSqlClaim.NotClaimed -> "Trino Brikk SQL"
    }
}

class BrikkTrinoActionBanner : HostSqlActionBanner() {
    public override fun actionNeeded(project: Project, file: VirtualFile): Action? {
        val psi = PsiManager.getInstance(project).findFile(file) ?: return null
        return BrikkHosts.actionNeeded(psi)?.let { bannerAction(project, it) }
    }
}

internal fun bannerAction(project: Project, action: BrikkAction): HostSqlActionBanner.Action? {
    if (action.kind == BrikkActionKind.DIALECT_PLUGIN_MISSING && action.dialect != BrikkDialect.TRINO) return null
    return HostSqlActionBanner.Action(action.message, action.fixText, Runnable {
        val build = action.buildFileUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        if (build != null) OpenFileDescriptor(project, build).navigate(true)
        else action.docsUrl?.let(BrowserUtil::browse)
    })
}

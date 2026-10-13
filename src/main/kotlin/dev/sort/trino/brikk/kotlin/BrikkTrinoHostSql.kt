package dev.sort.trino.brikk.kotlin

import com.intellij.lang.annotation.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import dev.brikk.house.intellij.*
import dev.brikk.house.sql.intellij.host.*
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.brikk.TrinoHostSql
import dev.sort.trino.sql.TrinoSqlDialect
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class BrikkTrinoHostSqlInjector : HostSqlInjector(TrinoHostSql, TrinoSqlDialect) {
    override val prefix = "-- dialect: trino\n"

    public override fun claim(host: PsiLanguageInjectionHost): HostSqlClaim {
        host.putUserData(BrikkTrinoProfile.KEY, null)
        host.putUserData(BrikkTrinoProfile.RELATIONS, null)
        val state = BrikkHosts.state(host)
        if (state.dialect != BrikkDialect.TRINO) return HostSqlClaim.NotClaimed
        val pending = state is BrikkHostState.Pending
        val context = when (state) {
            is BrikkHostState.Recognized -> state.context
            is BrikkHostState.Pending -> state.projection ?: return HostSqlClaim.Unavailable(state.reason)
            BrikkHostState.NotBrikk -> return HostSqlClaim.NotClaimed
        }
        val profile = BrikkTrinoProfile(context.binds, context.slots, context.entries.mapNotNull { it.problem })
        host.putUserData(BrikkTrinoProfile.KEY, profile)
        // Retained only until the next relation-feature migration commit.
        val pointer = SmartPointerManager.getInstance(host.project).createSmartPsiElementPointer(host)
        host.putUserData(BrikkTrinoProfile.RELATIONS) {
            (pointer.element?.let { BrikkHosts.state(it, includeShapes = true) } as? BrikkHostState.Recognized)
                ?.context?.takeIf { it.dialect == BrikkDialect.TRINO }?.relations.orEmpty()
        }
        val entries = HostSqlEntries(context.entries.map {
            HostSqlEntry(TextRange(it.range.start, it.range.end), it.replacement, it.placeholder)
        }, context.trimSteps.map {
            when (it.function) {
                "trimIndent" -> HostSqlTrim.Indent
                "trimMargin" -> HostSqlTrim.Margin(it.marginPrefix ?: "|")
                else -> error("Unsupported helper trim: ${it.function}")
            }
        })
        return HostSqlClaim.Injected(TrinoSqlDialect, entries, pending = pending, data = profile)
    }
}

class BrikkTrinoHostAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val host = element as? KtStringTemplateExpression ?: return
        val state = BrikkHosts.state(host) as? BrikkHostState.Recognized ?: return
        if (state.context.dialect != BrikkDialect.TRINO) return
        holder.newAnnotation(HighlightSeverity.INFORMATION, "Trino Brikk: PIPE editing enabled; inspection SQL is not executable.").range(host).create()
        state.context.entries.filterNot { it.placeholder }.forEach { entry ->
            entry.problem?.let {
                holder.newAnnotation(HighlightSeverity.ERROR, it)
                    .range(TextRange(entry.range.start, entry.range.end).shiftRight(host.textRange.startOffset)).create()
            }
        }
    }
}

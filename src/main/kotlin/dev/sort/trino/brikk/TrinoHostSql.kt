package dev.sort.trino.brikk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiLanguageInjectionHost
import dev.brikk.house.sql.intellij.host.HostSqlChannel
import dev.brikk.house.sql.intellij.host.HostSqlPlaceholderErrorFilter
import dev.brikk.house.sql.intellij.host.HostSqlRunConsoleVeto

fun interface TrinoInspectionHostGuard {
    fun isInspectionOnly(host: PsiLanguageInjectionHost): Boolean
}

object TrinoHostSql : HostSqlChannel("dev.sort.trino.brikk") {
    private val guards = ExtensionPointName.create<TrinoInspectionHostGuard>("dev.sort.trino-intellij-plugin.inspectionHostGuard")
    override fun claimsHost(host: PsiLanguageInjectionHost) = guards.extensionList.any { it.isInspectionOnly(host) }
}

class TrinoHostPlaceholderErrorFilter : HostSqlPlaceholderErrorFilter(TrinoHostSql)

class TrinoHostRunConsoleVeto : HostSqlRunConsoleVeto(TrinoHostSql)

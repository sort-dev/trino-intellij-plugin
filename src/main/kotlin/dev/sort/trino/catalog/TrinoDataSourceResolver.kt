package dev.sort.trino.catalog

import com.intellij.database.console.JdbcConsoleProvider
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.sort.trino.TrinoDbms

/**
 * Which Trino (Brikk) data source serves an editor, public seams only (ported from the duckdb
 * plugin's `DuckdbCatalogResolver`):
 *  1. a running/attached database console resolves through [JdbcConsoleProvider.getValidConsole]
 *     (the console's own data source), gated to our dbms;
 *  2. otherwise fall back to "the project has exactly one Trino (Brikk) data source" — ambiguous
 *     setups (0 or 2+) resolve to null rather than guessing, so auto-introspection stays quiet.
 *
 * Everything best-effort: any surprise resolves to null and the caller does nothing.
 */
object TrinoDataSourceResolver {

    fun resolve(file: PsiFile): LocalDataSource? {
        val project = file.project
        val virtualFile = file.originalFile.virtualFile
        if (virtualFile != null) {
            val console = runCatching { JdbcConsoleProvider.getValidConsole(project, virtualFile) }.getOrNull()
            val dataSource = console?.dataSource
            if (dataSource != null && dataSource.dbms == TrinoDbms.TRINO_BRIKK) return dataSource
        }
        return singleDataSource(project)
    }

    /** The project's single Trino (Brikk) data source; 0 or 2+ resolve to null (no guessing). */
    fun singleDataSource(project: Project): LocalDataSource? {
        val ours = runCatching {
            LocalDataSourceManager.getInstance(project).dataSources.filter { it.dbms == TrinoDbms.TRINO_BRIKK }
        }.getOrDefault(emptyList())
        return ours.singleOrNull()
    }
}

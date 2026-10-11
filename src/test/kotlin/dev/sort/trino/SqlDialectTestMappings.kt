package dev.sort.trino

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.sql.dialects.SqlLanguageDialect
import com.intellij.util.FileContentUtil
import java.lang.reflect.InvocationTargetException

/** The 263 public workspace setter supersedes storage-only setMapping in fixtures. */
internal fun setSqlDialectMapping(project: Project, file: VirtualFile?, dialect: SqlLanguageDialect?) {
    val setter = try {
        Class.forName("com.intellij.sql.dialects.SqlDialectMappingUtilsKt").methods.singleOrNull {
            it.name == "setDialectMappingSync" && it.parameterCount == 3
        }
    } catch (_: ClassNotFoundException) { null }
    if (setter == null) SqlDialectMappings.getInstance(project).setMapping(file, dialect)
    else WriteAction.run<RuntimeException> {
        try {
            setter.invoke(null, project, file, dialect)
            FileContentUtil.reparseFiles(project, listOfNotNull(file), true)
        } catch (e: InvocationTargetException) { throw e.targetException }
    }
}

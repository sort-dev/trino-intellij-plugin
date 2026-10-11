package dev.sort.trino.pipes

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.parser.SqlScriptSnapshot
import dev.brikk.house.sql.intellij.recoverEditorOperation
import dev.sort.trino.brikk.BrikkTrinoProfile

internal inline fun <T> runPipeCatching(action: () -> T): Result<T> = recoverEditorOperation(action)

object TrinoPipes {
    const val PROPERTY = "trino.pipes"
    const val MARKER = "|>"
    fun isEnabled(project: Project?): Boolean = project != null && !project.isDisposed && !project.isDefault &&
        !"false".equals(System.getProperty(PROPERTY), true) && project.service<TrinoPipesSettings>().enabled
    fun isEnabled(file: PsiFile): Boolean = !"false".equals(System.getProperty(PROPERTY), true) &&
        (BrikkTrinoProfile.forFile(file) != null || isEnabled(file.project))

    data class Chunk(val text: String, val startOffset: Int, val endOffset: Int,
                     val hasPipeOperator: Boolean, val hasSql: Boolean, val boundaryError: String?)
    fun chunks(text: String): List<Chunk> = SqlScriptSnapshot.parse(text, "trino").statements.map {
        Chunk(text.substring(it.start, it.end), it.start, it.end, it.hasPipe, it.hasSql, it.boundaryError)
    }
    fun containsPipeOperator(text: String): Boolean = MARKER in text && chunks(text).any { it.hasPipeOperator }
    fun chunkAt(text: String, offset: Int): Chunk? = chunks(text).let { chunks ->
        chunks.firstOrNull { offset >= it.startOffset && offset < it.endOffset }
            ?: chunks.lastOrNull()?.takeIf { offset == it.endOffset }
    }
}

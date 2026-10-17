package dev.sort.trino.validate

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.intellij.diagnostics.NativeErrorAnnotator

internal fun TrinoErrorAnnotator.annotations(file: PsiFile, outcome: NativeErrorAnnotator.Outcome? =
    doAnnotate(collectInformation(file))): List<Pair<TextRange, String>> {
    val holder = AnnotationHolderImpl(AnnotationSession(file), false)
    holder.runAnnotatorWithContext(file, Annotator { _, annotations -> apply(file, outcome, annotations) })
    holder.assertAllAnnotationsCreated()
    return holder.map { TextRange(it.startOffset, it.endOffset) to it.message.orEmpty() }
}

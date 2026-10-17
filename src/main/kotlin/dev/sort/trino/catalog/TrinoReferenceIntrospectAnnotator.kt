package dev.sort.trino.catalog

import com.intellij.database.model.ObjectKind
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlReferenceExpression
import dev.brikk.house.sql.intellij.catalog.OutOfScopeClassifier
import dev.brikk.house.sql.intellij.catalog.ReferenceScope

/** Loading a query also loads its listed, un-introspected namespaces, one level at a time. */
class TrinoReferenceIntrospectAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        val source = trinoDataSourceResolver.resolve(file).dataSource ?: return
        val requests = file.project.service<TrinoAutoIntrospectService>()
        if (!requests.profile.autoIntrospect) return
        val classifier = OutOfScopeClassifier(requests)
        for (reference in PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java)) {
            if (reference.referenceElementType.targetKind != ObjectKind.TABLE || reference.resolve() != null) continue
            val target = classifier.classify(reference) as? ReferenceScope.OutOfScope ?: continue
            if (target.dataSource.uniqueId == source.uniqueId) requests.request(file, source, target.namespace)
        }
    }
}

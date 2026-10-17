package dev.sort.trino.sql

import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType
import com.intellij.sql.dialects.postgres.PgElementFactory
import com.intellij.sql.psi.SqlCompositeElementTypes

class TrinoElementFactory : PgElementFactory() {
    override fun createElementNode(type: IElementType): CompositeElement? =
        if (type == SqlCompositeElementTypes.SQL_FUNCTION_CALL) TrinoTableFunctionCall(type) else super.createElementNode(type)
}

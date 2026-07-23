package dev.sort.trino.sql

import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.sql.dialects.base.SqlElementFactoryBase
import com.intellij.sql.dialects.base.SqlParserDefinitionBase
import com.intellij.sql.dialects.postgres.PgElementFactory
import com.intellij.sql.psi.stubs.elementTypes.SqlFileElementType

/**
 * Trino (Brikk) parsing on the PG foundation: [TrinoLexer] (token-layer seam, pass-through until
 * the census demands rules) + PG element factory + [TrinoPsiParser] (statement-boundary
 * dispatch).
 */
class TrinoParserDefinition : SqlParserDefinitionBase() {
    override fun createElementFactory(): SqlElementFactoryBase = PgElementFactory()
    override fun createLexer(project: Project): Lexer = TrinoLexer()
    override fun createParser(project: Project): PsiParser = TrinoPsiParser()
    override fun getFileNodeType(): IFileElementType = FILE

    private companion object {
        private val FILE = SqlFileElementType("TRINO_BRIKK_SQL_FILE", TrinoSqlDialect.INSTANCE)
    }
}

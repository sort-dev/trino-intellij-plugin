package dev.sort.trino.pipes

import com.intellij.database.console.session.DatabaseSession
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.intellij.pipe.NativeSqlValidator
import dev.brikk.house.sql.intellij.pipe.NativeVerdict
import dev.brikk.house.sql.intellij.pipe.PipeDialect
import dev.brikk.house.sql.intellij.pipe.ValidationTiming
import dev.sort.trino.TrinoDbms
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.sql.TrinoSqlDialect
import dev.sort.trino.validate.TrinoStatementValidator

object TrinoPipeDialect : PipeDialect {
    override val dialect = "trino"
    override val label = "Trino PIPE"
    override val vetoProperty = "trino.pipes"
    override val language get() = TrinoSqlDialect.INSTANCE
    override fun isProjectEnabled(project: Project) = project.service<TrinoPipesSettings>().enabled
    override fun enablesEditing(file: PsiFile) = BrikkTrinoProfile.forFile(file) != null
    override fun isInspectionHost(file: PsiFile) = BrikkTrinoProfile.forFile(file) != null
    override fun enablesPreview(file: PsiFile) = !isInspectionHost(file)
    override fun ownsSession(session: DatabaseSession) = session.connectionPoint.dbms === TrinoDbms.TRINO_BRIKK
    override val validator = object : NativeSqlValidator {
        override val timing = ValidationTiming.Lowering
        override fun validate(sql: String, file: PsiFile?): NativeVerdict =
            TrinoStatementValidator.parse(sql)?.let { NativeVerdict.Rejected(it.message) } ?: NativeVerdict.Accepted
    }
}

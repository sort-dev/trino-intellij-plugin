package dev.sort.trino.pipes

import com.intellij.database.console.session.DatabaseSession
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.intellij.pipe.NativeSqlValidator
import dev.brikk.house.sql.intellij.pipe.PipeDialect
import dev.brikk.house.sql.intellij.pipe.ValidationTiming
import dev.sort.trino.TrinoDbms
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.brikk.TrinoHostSql
import dev.sort.trino.sql.TrinoSqlDialect
import dev.sort.trino.validate.TrinoNativeValidation
import dev.sort.trino.validate.toNativeVerdict

object TrinoPipeDialect : PipeDialect {
    override val dialect = "trino"
    override val label = "Trino PIPE"
    override val vetoProperty = "trino.pipes"
    override val language get() = TrinoSqlDialect
    override fun isProjectEnabled(project: Project) = project.service<TrinoPipesSettings>().enabled
    override val hostSql get() = TrinoHostSql
    // The injection marker is attached after its initial PSI parse; copied host facts are already available.
    override fun enablesEditing(file: PsiFile) = hostSql.marker(file) != null || BrikkTrinoProfile.forFile(file) != null
    override fun ownsSession(session: DatabaseSession) = session.connectionPoint.dbms === TrinoDbms.TRINO_BRIKK
    override val validator = object : NativeSqlValidator {
        override val timing = ValidationTiming.Lowering
        override fun validate(sql: String, file: PsiFile?) = TrinoNativeValidation.verify(sql).toNativeVerdict()
    }
}

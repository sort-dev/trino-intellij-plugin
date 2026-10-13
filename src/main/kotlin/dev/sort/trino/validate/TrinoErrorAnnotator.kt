package dev.sort.trino.validate

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.diagnostics.*
import dev.brikk.house.sql.intellij.diagnostics.NativeErrorAnnotator
import dev.brikk.house.sql.intellij.diagnostics.NativeErrorProfile
import dev.brikk.house.sql.intellij.pipe.NativeSqlValidator
import dev.brikk.house.sql.intellij.pipe.PipeTranslation
import dev.brikk.house.sql.intellij.pipe.isEditingEnabled
import dev.brikk.house.sql.intellij.pipe.lower
import dev.brikk.house.sql.parser.ParseError
import dev.brikk.house.sql.parser.SqlStatementRange
import dev.brikk.house.sql.parser.TokenError
import dev.brikk.house.sql.shape.ShapeError
import dev.brikk.house.sql.shape.SqlFragment
import dev.brikk.house.sql.verify.TrinoVerifier
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.brikk.TrinoHostSql
import dev.sort.trino.pipes.TrinoPipeDialect
import dev.sort.trino.pipes.TrinoPipesSettings
import dev.sort.trino.sql.TrinoSqlDialect
import kotlin.time.Duration.Companion.seconds

class TrinoErrorAnnotator : NativeErrorAnnotator(TrinoNativeErrorProfile)

object TrinoNativeErrorProfile : NativeErrorProfile {
    override val language get() = TrinoSqlDialect
    override val injectionHosts get() = setOfNotNull(Language.findLanguageByID("kotlin"))
    override val dialect = "trino"
    override val label = "Trino"
    override val hostSql get() = TrinoHostSql
    override val sizeCap = 1_000_000
    override val budget = 2.seconds
    private val nativeValidator = NativeSqlValidator.of(TrinoVerifier())
    private data class Context(val brikk: BrikkTrinoProfile?, val pipesEnabled: Boolean)
    private val contextKey = Key.create<Context>("trino.native.error.context")

    // These facts may need PSI. The worker hooks below read only this copied user data.
    override fun validator(file: PsiFile): NativeSqlValidator {
        file.putUserData(contextKey, Context(BrikkTrinoProfile.forFile(file), TrinoPipeDialect.isEditingEnabled(file)))
        return nativeValidator
    }

    override fun modificationCount(file: PsiFile): Long =
        31 * file.project.service<TrinoPipesSettings>().diagnosticRevision + (file.getUserData(contextKey)?.hashCode()?.toLong() ?: 0)

    override fun statementDiagnostics(file: PsiFile, script: String, statement: SqlStatementRange): List<SqlDiagnostic>? {
        val context = file.getUserData(contextKey) ?: return null
        val range = verificationRange(script, statement) ?: return emptyList()
        val text = script.substring(range.first, range.last + 1)
        if (text.length > sizeCap) return emptyList()
        if (context.brikk != null) {
            if (context.brikk.issues.isNotEmpty()) return emptyList()
            val error = try {
                SqlFragment(text, dialect).ast
                return emptyList()
            } catch (e: ParseError) {
                val first = e.errors.firstOrNull()
                NativeError("Trino Brikk: ${first?.description ?: e.message}", first?.start ?: 0, first?.end)
            } catch (e: TokenError) {
                NativeError("Trino Brikk: ${e.message}", 0)
            } catch (e: ShapeError) {
                NativeError("Trino Brikk: ${e.message}", 0)
            }
            return customError(script, range, error)
        }
        if (!context.pipesEnabled || !statement.hasPipe) return null
        val refusal = when (val translated = TrinoPipeDialect.lower(text)) {
            is PipeTranslation.Refused -> translated
            is PipeTranslation.Lowered -> translated.blocker?.let { PipeTranslation.Refused(null, it) }
            else -> null
        } ?: return emptyList()
        return customError(script, range, NativeError("${TrinoPipeDialect.label}: ${refusal.message}",
            refusal.range?.start ?: text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0), refusal.range?.end))
    }

    private fun customError(script: String, range: IntRange, error: NativeError): List<SqlDiagnostic> =
        combineStatementDiagnostics(script, range, emptyList(), NativeOutcome.Rejected(listOf(error)))
            .diagnostics.map { it.copy(source = DiagnosticSource.OTHER) }
}

package dev.sort.trino.pipes

import com.intellij.database.actions.RunQueryAction
import com.intellij.database.console.JdbcConsole
import com.intellij.database.console.JdbcConsoleProvider
import com.intellij.database.script.ScriptModel
import com.intellij.database.script.translator.TranslateException
import com.intellij.database.settings.DatabaseSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.text.StringUtil
import dev.brikk.house.sql.intellij.*
import dev.brikk.house.sql.shape.ParamStyle
import dev.brikk.house.sql.shape.parseParameterOccurrences
import dev.sort.trino.TrinoDbms
import dev.sort.trino.brikk.BrikkTrinoProfile

class TrinoPipesActionConfiguration : CooperativeExecuteConfiguration() {
    override val interceptor = ExecuteInterceptor(PipesExecuteInterceptor::handle)
}
class TrinoPipesActionPromoter : CooperativeExecutePromoter()

internal object PipesExecuteInterceptor {
    fun handle(e: AnActionEvent, option: DatabaseSettings.ExecOption): ExecutionClaim {
        if (BrikkTrinoProfile.forFile(e.getData(CommonDataKeys.PSI_FILE)) != null) {
            notify(e.project, "Brikk SQL is an inspection view. Execute the compiled relation with its bindings.")
            return ExecutionClaim.HANDLED
        }
        val console = JdbcConsole.findConsole(e) ?: return ExecutionClaim.DECLINED
        if (console.session.connectionPoint.dbms !== TrinoDbms.TRINO_BRIKK || !TrinoPipes.isEnabled(console.project))
            return ExecutionClaim.DECLINED
        val editor = e.getData(CommonDataKeys.EDITOR) ?: (e.getData(PlatformCoreDataKeys.FILE_EDITOR) as? TextEditor)?.editor
            ?: return ExecutionClaim.DECLINED
        val text = editor.document.text
        val current = TrinoPipes.chunkAt(text, editor.caretModel.offset)
        val tokens = current?.takeIf { it.boundaryError == null }?.let {
            dev.brikk.house.sql.parser.Tokenizer(dev.brikk.house.sql.parser.TokenizerConfigs.forName("trino"))
                .tokenizeForEditor(it.text).tokens.filterNot { token -> token.tokenType == dev.brikk.house.sql.parser.TokenType.SEMICOLON }
        }
        val outside = current == null || !current.hasSql || tokens != null &&
            (tokens.isEmpty() || editor.caretModel.offset < current.startOffset + tokens.first().start ||
                editor.caretModel.offset > current.startOffset + tokens.last().end + 1)
        val selection = editor.selectionModel.selectedText
        val candidate = if (selection != null) {
            if (option.execSelection == DatabaseSettings.EXECUTE_SELECTION_SMART_EXPAND) {
                val start = editor.selectionModel.selectionStart
                val end = editor.selectionModel.selectionEnd
                val selected = TrinoPipes.chunks(text).filter { it.startOffset < end && it.endOffset > start }
                if (selected.isEmpty()) selection else text.substring(selected.first().startOffset, selected.last().endOffset)
            } else selection
        } else if (outside) when (option.execOutside) {
            DatabaseSettings.EXECUTE_OUTSIDE_WHOLE_SCRIPT -> text
            DatabaseSettings.EXECUTE_OUTSIDE_SCRIPT_TAIL -> {
                // Preserve the opening of leading comments; a raw caret substring could
                // invent an unmatched delimiter from the middle of comment text.
                val start = if (current != null && tokens?.firstOrNull()?.let {
                    current.startOffset + it.start >= editor.caretModel.offset
                } == true) current.startOffset else TrinoPipes.chunks(text)
                    .firstOrNull { it.hasSql && it.startOffset >= editor.caretModel.offset }?.startOffset ?: text.length
                text.substring(start)
            }
            else -> return ExecutionClaim.DECLINED
        } else when (option.execInside) {
            DatabaseSettings.EXECUTE_INSIDE_WHOLE_SCRIPT -> text
            DatabaseSettings.EXECUTE_INSIDE_SHOW_CHOOSER, DatabaseSettings.EXECUTE_INSIDE_BATCH -> text
            DatabaseSettings.EXECUTE_INSIDE_SCRIPT_TAIL -> text.substring(current?.startOffset ?: editor.caretModel.offset)
            else -> current?.text ?: return ExecutionClaim.DECLINED
        }
        if (!TrinoPipes.containsPipeOperator(candidate)) return ExecutionClaim.DECLINED
        TrinoPipes.chunks(candidate).firstOrNull { it.boundaryError != null }?.let {
            notify(console.project, it.boundaryError!!)
            return ExecutionClaim.HANDLED
        }
        // Claim before stock preparation. Cancelled preparation must never run raw SQL.
        val snapshot = EditorSnapshot.capture(editor.document)
        val owner = console
        object : RunQueryAction(1) {
            override fun actionPerformed(e: AnActionEvent) = super.actionPerformed(e)
            override fun getExecOption() = option
            override fun invokeImpl(e: AnActionEvent, console: JdbcConsole?, info: JdbcConsoleProvider.Info) {
                if (console == null) return
                if (console !== owner) throw SqlExecutionRefusal("The destination console changed during preparation")
                snapshot.requireCurrent()
                JdbcConsoleProvider.chooseStatements(info, "Nothing to run", true) { selected ->
                    snapshot.requireCurrent()
                    executePipeModel(console, editor, selected, info.execOption)
                }
            }
        }.actionPerformed(e)
        return ExecutionClaim.HANDLED
    }
}

internal class TrinoExecutionAdapter(private val firstStage: Boolean = false) : SqlExecutionAdapter {
    override fun claims(text: String) = firstStage || TrinoPipes.containsPipeOperator(text)
    override fun parameters(text: String): List<SqlParameterSpan> = try {
        parseParameterOccurrences(text.trim().removeSuffix(";"), "trino")
            .filter { it.style == ParamStyle.NAMED_COLON && it.name != null }.map {
                val leading = text.length - text.trimStart().length
                SqlParameterSpan(it.name!!, it.range.start + leading, it.range.end + leading)
            }
    } catch (e: dev.brikk.house.sql.parser.ParseError) {
        throw SqlExecutionRefusal(e.message ?: "Invalid PIPE SQL")
    } catch (e: dev.brikk.house.sql.parser.TokenError) {
        throw SqlExecutionRefusal(e.message ?: "Invalid PIPE token")
    } catch (e: dev.brikk.house.sql.shape.ShapeError) {
        throw SqlExecutionRefusal(e.message ?: "Invalid PIPE statement")
    }
    override fun translate(text: String, beforeSubstitution: Boolean): RenderedSql =
        when (val result = TrinoPipesEngine.transpile(text, beforeSubstitution, firstStage)) {
            is TrinoPipesEngine.Transpile.Ok -> {
                result.executionError?.let { throw SqlExecutionRefusal(it.message) }
                val trim = text.length - text.trimStart().length
                RenderedSql(result.sql, text) map@ { line, column ->
                    if (line < 1 || column < 1) return@map null
                    val outputLine = result.sql.lineSequence().elementAtOrNull(line - 1) ?: return@map null
                    if (column - 1 > outputLine.codePointCount(0, outputLine.length)) return@map null
                    val utf16Column = outputLine.offsetByCodePoints(0, column - 1) + 1
                    val position = result.result.mapErrorToSource(line, utf16Column) ?: return@map null
                    if (position.start < 0 || position.end < position.start || position.end + trim >= text.length) return@map null
                    com.intellij.openapi.util.TextRange(position.start + trim, position.end + trim + 1)
                }
            }
            is TrinoPipesEngine.Transpile.Err -> throw SqlExecutionRefusal(result.message)
            TrinoPipesEngine.Transpile.NotPipe -> throw SqlExecutionRefusal("PIPE statement was not translated")
        }
}

internal fun <E> executePipeModel(console: JdbcConsole, editor: Editor, selected: ScriptModel<E>,
                                option: DatabaseSettings.ExecOption, allowFirstQueryStage: Boolean = false) {
    try {
        val session = console.session
        val target = console.dataSource
        val url = target?.url
        val model = TranslatingScriptModel(selected, editor, TrinoExecutionAdapter(allowFirstQueryStage))
        if (!console.beforeExecuteQueries(model)) return
        model.finalizeTranslations(console.pStorage)
        if (!console.isValid || console.session !== session || console.dataSource !== target || target?.url != url ||
            session.connectionPoint.dbms !== TrinoDbms.TRINO_BRIKK)
            throw SqlExecutionRefusal("The destination changed during parameter preparation. Run the query again.")
        if (!TrinoPipes.isEnabled(console.project)) throw SqlExecutionRefusal("PIPE was disabled during preparation")
        if (console.session.clientsWithFile.isEmpty()) throw SqlExecutionRefusal("No attached console client")
        console.executeQueries(editor, model, option)
    } catch (e: SqlExecutionRefusal) {
        notify(console.project, e.message ?: "PIPE execution refused")
    } catch (e: TranslateException) {
        notify(console.project, e.message ?: "Parameter substitution failed")
    }
}

internal fun notify(project: com.intellij.openapi.project.Project?, message: String) {
    NotificationGroupManager.getInstance().getNotificationGroup("Trino PIPE")
        .createNotification("Trino PIPE", StringUtil.escapeXmlEntities(message).replace("\n", "<br>"), NotificationType.ERROR)
        .notify(project)
}

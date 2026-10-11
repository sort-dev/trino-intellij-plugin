package dev.sort.trino.pipes

import com.intellij.database.actions.ShowSqlParametersPanelAction
import com.intellij.database.run.ConsoleDataRequest
import com.intellij.database.console.JdbcConsole
import com.intellij.database.console.client.*
import com.intellij.database.datagrid.*
import com.intellij.database.settings.DatabaseSettings
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.*
import dev.sort.trino.sql.TrinoSqlDialect
import dev.brikk.house.sql.intellij.testing.RecordingConsole

/** Real console and platform preparation. Only the session and producer are recording doubles. */
class TrinoPipesExecutionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<TrinoPipesSettings>().enabled = true
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    override fun tearDown() {
        try { project.service<TrinoPipesSettings>().enabled = false; PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
        finally { super.tearDown() }
    }
    fun testAllVariantsSubmitGeneratedSqlWithOriginalOwnerAndEditor() {
        val sql = "FROM offline_rows |> SELECT id |> WHERE id > 0"
        Fixture(sql).use { f ->
            for (variant in 1..4) {
                f.editor.selectionModel.setSelection(0, sql.length)
                f.execute(variant)
                assertEquals(f.notifications.map { it.content }.toString(), variant, f.requests.size)
                val request = f.requests.last() as DataRequest.QueryRequest
                assertEquals((TrinoPipesEngine.transpile(sql) as TrinoPipesEngine.Transpile.Ok).sql, request.query)
                assertSame(f.console, request.owner)
                assertSame(f.editor, (request as DataRequest.CoupledWithEditor).editor)
                assertEquals(sql, f.editor.document.text)
                assertEmpty(f.previous)
            }
        }
    }
    fun testOrdinaryQuotedMarkersDelegate() {
        for (sql in listOf("SELECT '|>'", "SELECT /* |> */ 1", "SELECT 1")) Fixture(sql).use { f ->
            val event = f.execute()
            assertEquals(listOf(event), f.previous)
            assertEmpty(f.requests)
        }
    }
    fun testUnselectedPipelineUsesCompleteStatement() {
        Fixture("SELECT 1; FROM offline_rows |> LIMIT 2; SELECT 3;").use { f ->
            f.editor.caretModel.moveToOffset(f.editor.document.text.indexOf("LIMIT"))
            withOption(DatabaseSettings.ExecOption().apply { execInside = DatabaseSettings.EXECUTE_INSIDE_SMALLEST }) { f.execute() }
            assertEquals(f.notifications.map { it.content }.toString(), 1, f.requests.size)
            assertFalse((f.requests.single() as DataRequest.QueryRequest).query.contains("|>"))
        }
    }
    fun testOutsideStatementWholeScriptOptionStillTranslatesPipes() {
        Fixture("SELECT 1; FROM offline_rows |> SELECT id; -- caret outside statements").use { f ->
            f.editor.caretModel.moveToOffset(f.editor.document.textLength)
            withOption(DatabaseSettings.ExecOption().apply {
                execInside = DatabaseSettings.EXECUTE_INSIDE_SMALLEST
                execOutside = DatabaseSettings.EXECUTE_OUTSIDE_WHOLE_SCRIPT
            }) {
                f.execute()
                assertEquals(f.notifications.map { it.content }.toString(), 1, f.requests.size)
                (f.requests.single() as ConsoleDataRequest).onFinished()
            }
            assertEquals(2, f.requests.size)
            assertFalse((f.requests.last() as DataRequest.QueryRequest).query.contains("|>"))
            assertEmpty(f.previous)
        }
    }
    fun testSmartExpandedSelectionClaimsTheEnclosingPipeline() {
        Fixture("FROM offline_rows |> WHERE id > 0 |> SELECT id").use { f ->
            val text = f.editor.document.text
            f.editor.selectionModel.setSelection(text.indexOf("WHERE"), text.indexOf(" |> SELECT"))
            withOption(DatabaseSettings.ExecOption().apply { execSelection = DatabaseSettings.EXECUTE_SELECTION_SMART_EXPAND }) { f.execute() }
            assertEquals(f.notifications.map { it.content }.toString(), 1, f.requests.size)
            assertFalse((f.requests.single() as DataRequest.QueryRequest).query.contains("|>"))
            assertEmpty(f.previous)
        }
    }
    fun testOutsideTailDoesNotRetokenizeTheMiddleOfAComment() {
        Fixture("SELECT 0; /* ) caret here */ FROM offline_rows |> SELECT id;").use { f ->
            f.editor.caretModel.moveToOffset(f.editor.document.text.indexOf("caret"))
            withOption(DatabaseSettings.ExecOption().apply {
                execInside = DatabaseSettings.EXECUTE_INSIDE_SMALLEST
                execOutside = DatabaseSettings.EXECUTE_OUTSIDE_SCRIPT_TAIL
            }) { f.execute() }
            assertEquals(f.notifications.map { it.content }.toString(), 1, f.requests.size)
            assertFalse((f.requests.single() as DataRequest.QueryRequest).query.contains("|>"))
            assertEmpty(f.previous)
        }
    }
    fun testLaterPipeFailureSubmitsNeitherOrdinaryNorPipeStatements() {
        Fixture("SELECT 1; FROM t |> BOGUS 2;").use { f ->
            f.editor.selectionModel.setSelection(0, f.editor.document.textLength)
            withOption(DatabaseSettings.ExecOption().apply { execSelection = DatabaseSettings.EXECUTE_SELECTION_EXACTLY_SCRIPT }) { f.execute() }
            assertEmpty(f.requests)
            assertEmpty(f.previous)
            assertFalse(f.notifications.isEmpty())
        }
    }
    fun testEditDuringParameterPreparationNotifiesWithoutSubmitting() {
        Fixture("FROM offline_rows |> WHERE id = :id |> SELECT id").use { f ->
            ShowSqlParametersPanelAction.getStorage(f.console).putValue("id", "17")
            var edited = false
            f.recording.beforeSessionCall = { method ->
                if (!edited && method == "getState") {
                    edited = true
                    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                        f.editor.document.insertString(f.editor.document.textLength, " ")
                    }
                }
            }
            f.execute()
            assertTrue("Preparation hook was not reached", edited)
            assertEmpty(f.requests)
            assertEmpty(f.previous)
            assertFalse(f.notifications.isEmpty())
        }
    }
    fun testEditDuringStockPreparationNotifiesWithoutSubmitting() {
        Fixture("FROM offline_rows |> SELECT id").use { f ->
            val connection = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect()
            var edited = false
            connection.subscribe(com.intellij.AppTopics.FILE_DOCUMENT_SYNC, object : com.intellij.openapi.fileEditor.FileDocumentManagerListener {
                override fun beforeAllDocumentsSaving() {
                    if (!edited) {
                        edited = true
                        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                            f.editor.document.insertString(f.editor.document.textLength, " ")
                        }
                    }
                }
            })
            try {
                f.execute()
                assertTrue(edited)
                assertEmpty(f.requests)
                assertEmpty(f.previous)
                assertFalse(f.notifications.isEmpty())
            } finally {
                connection.disconnect()
            }
        }
    }
    fun testCancellationDuringPreparationPropagatesWithoutSubmitting() {
        for (cancellation in listOf(com.intellij.openapi.progress.ProcessCanceledException(), java.util.concurrent.CancellationException())) {
            Fixture("FROM offline_rows |> SELECT id").use { f ->
                f.recording.beforeSessionCall = { method -> if (method == "getClientsWithFile") throw cancellation }
                val thrown = kotlin.runCatching { f.execute() }.exceptionOrNull()
                assertSame(cancellation, thrown)
                assertEmpty(f.requests)
                assertEmpty(f.previous)
                assertEmpty(f.notifications)
            }
        }
    }
    fun testMissingSubmissionClientNeverDelegatesRawSql() {
        Fixture("FROM offline_rows |> SELECT id").use { f ->
            f.console.session.detach(f.console)
            f.execute()
            assertEmpty(f.requests)
            assertEmpty(f.previous)
        }
    }
    fun testSubmissionFailureDoesNotRetryThroughPredecessor() {
        Fixture("FROM offline_rows |> SELECT id").use { f ->
            val failure = IllegalStateException("recording producer failed")
            f.recording.onSubmit = { throw failure }
            try { f.execute(); fail("Submission failure must propagate") } catch (e: IllegalStateException) { assertSame(failure, e) }
            assertEquals(1, f.requests.size)
            assertFalse((f.requests.single() as DataRequest.QueryRequest).query.contains("|>"))
            assertEmpty(f.previous)
        }
    }
    fun testParametersMixedScriptAndNewTabsUseNormalRequestChain() {
        Fixture("SELECT 1; FROM offline_rows |> WHERE id = :id |> SELECT id; SELECT 2;").use { f ->
            ShowSqlParametersPanelAction.getStorage(f.console).putValue("id", "17")
            withOption(DatabaseSettings.ExecOption().apply { execInside = DatabaseSettings.EXECUTE_INSIDE_WHOLE_SCRIPT; newTab = true }) {
                f.execute()
                assertEquals(f.notifications.map { it.content }.toString(), 1, f.requests.size)
                repeat(2) { (f.requests.last() as ConsoleDataRequest).onFinished() }
            }
            assertEquals(3, f.requests.size)
            val requests = f.requests.map { it as ConsoleDataRequest }
            assertEquals("SELECT 1", requests[0].query.trim())
            assertEquals("SELECT 2", requests[2].query.trim())
            assertTrue(requests[1].query, "17" in requests[1].query)
            assertFalse(":id" in requests[1].query || "|>" in requests[1].query)
            assertTrue(requests.all { it.newTab && it.owner === f.console })
            assertEmpty(f.previous)
        }
    }
    fun testRunToFirstStage() {
        for (source in listOf("FROM offline_rows |> WHERE id > 0 |> LIMIT 2", "SELECT id FROM offline_rows |> WHERE id > 0 |> LIMIT 2")) Fixture(source).use { f ->
            f.editor.caretModel.moveToOffset(3)
            assertNotNull("PIPE context missing for $source; language=${f.file.language}", TrinoPipesUi.chunk(f.file, f.editor))
            assertNotNull("Console lookup missing for $source; valid=${f.console.isValid}",
                com.intellij.database.console.JdbcConsoleProvider.getValidConsole(project, f.file.viewProvider.virtualFile))
            TrinoPipesUi.runToStage(project, f.file, f.editor)
            assertEquals("$source: ${f.notifications.map { it.content }}", 1, f.requests.size)
            val sql = (f.requests.single() as DataRequest.QueryRequest).query
            assertFalse("WHERE" in sql || "|>" in sql)
        }
    }
    fun testConfiguredIdeParametersInExpressionsAndLiterals() {
        val settings = DatabaseSettings.getSettings()
        val saved = settings.userPatterns
        settings.userPatterns = com.intellij.database.settings.UserPatterns().apply {
            inScripts = true; inLiterals = true; processStrings = true
            patterns = mutableListOf(com.intellij.database.settings.UserPatterns.ParameterPattern("\\$\\{([^\\{\\}]*)\\}", "", "\${name}").enabled(true, true))
        }
        settings.patternCache.clear()
        try {
            Fixture("FROM offline_rows |> WHERE id = \${id} AND status = '\${status}' |> LIMIT \${rows}").use { f ->
                val storage = ShowSqlParametersPanelAction.getStorage(f.console)
                storage.putValue("id", "17"); storage.putValue("status", "open"); storage.putValue("rows", "3")
                f.editor.selectionModel.setSelection(0, f.editor.document.textLength)
                f.execute()
                assertEquals(f.notifications.map { it.content }.toString(), 1, f.requests.size)
                val sql = (f.requests.single() as DataRequest.QueryRequest).query
                assertTrue(sql, "17" in sql && "'open'" in sql && "LIMIT 3" in sql)
                assertFalse(sql, "\${" in sql || "|>" in sql)
            }
        } finally { settings.userPatterns = saved; settings.patternCache.clear() }
    }
    fun testDuckdbShapedSecondConsumerUsesSharedPreparationWithoutTrinoPolicy() {
        Fixture("FROM offline_rows |> SELECT id").use { f ->
            val adapter = object : SqlExecutionAdapter {
                override fun claims(text: String) = TrinoPipes.containsPipeOperator(text)
                override fun translate(text: String, beforeSubstitution: Boolean): RenderedSql {
                    val result = dev.brikk.house.sql.shape.SqlFragment(text, "duckdb").toExecutable("duckdb", pretty = true)
                    return RenderedSql(result.sql, text)
                }
            }
            val model = TranslatingScriptModel(f.console.scriptModel, f.editor, adapter)
            model.finalizeTranslations(f.console.pStorage)
            f.console.executeQueries(f.editor, model, selectionExecuteOption())
            assertEquals(1, f.requests.size)
            assertFalse((f.requests.single() as DataRequest.QueryRequest).query.contains("|>"))
        }
    }
    private fun withOption(option: DatabaseSettings.ExecOption, action: () -> Unit) {
        val settings = DatabaseSettings.getSettings()
        val previous = settings.execOptions[0]
        try { settings.execOptions[0] = option; action() } finally { settings.execOptions[0] = previous }
    }
    private inner class Fixture(sql: String) : AutoCloseable {
        private val identity = java.util.UUID.randomUUID().toString()
        private val vf = myFixture.configureByText("offline-pipe-$identity.sql", sql).virtualFile
        val file get() = PsiManager.getInstance(project).findFile(vf)!!
        val editor = myFixture.editor
        val requests get() = recording.requests
        val previous = mutableListOf<AnActionEvent>()
        val notifications = mutableListOf<Notification>()
        private val connection = project.messageBus.connect()
        val recording: RecordingConsole
        val console get() = recording.console
        init {
            dev.sort.trino.setSqlDialectMapping(project, vf, TrinoSqlDialect.INSTANCE)
            recording = RecordingConsole(project, vf, "trino-brikk")
            assertTrue("New console must be valid", console.isValid)
            connection.subscribe(Notifications.TOPIC, object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == "Trino PIPE") { notification.setSuppressShowingPopup(true); notifications.add(notification) }
                }
            })
        }
        fun execute(variant: Int = 1): AnActionEvent {
            val e = AnActionEvent.createFromDataContext("Trino recording console", Presentation(), SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.EDITOR, editor).add(CommonDataKeys.PSI_FILE, file)
                .add(CommonDataKeys.VIRTUAL_FILE, vf).add(SessionClientHolder.CLIENT_KEY, console).build())
            val previousAction = object : AnAction() { override fun actionPerformed(e: AnActionEvent) { previous.add(e) } }
            CooperativeExecuteAction(if (variant == 4) -1 else variant, previousAction, ExecuteInterceptor(PipesExecuteInterceptor::handle), variant == 4).actionPerformed(e)
            return e
        }
        override fun close() {
            connection.disconnect()
            notifications.forEach { it.expire() }
            try {
                recording.close()
            } finally {
                dev.sort.trino.setSqlDialectMapping(project, vf, null)
            }
        }
    }
}

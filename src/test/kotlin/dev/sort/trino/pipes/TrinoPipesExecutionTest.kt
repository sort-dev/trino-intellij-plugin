package dev.sort.trino.pipes

import com.intellij.database.actions.ShowSqlParametersPanelAction
import com.intellij.database.console.JdbcConsole
import com.intellij.database.console.client.*
import com.intellij.database.datagrid.*
import com.intellij.database.run.ConsoleDataRequest
import com.intellij.database.settings.DatabaseSettings
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.*
import dev.brikk.house.sql.intellij.pipe.PipeExecuteInterceptor
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.sort.trino.sql.TrinoSqlDialect

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
                assertSame(f.editor, (f.requests.last() as DataRequest.CoupledWithEditor).editor)
                assertEquals(sql, f.editor.document.text)
            }
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
            assertEmpty(f.notifications)
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
            dev.brikk.house.sql.intellij.testing.setSqlDialectMapping(project, vf, TrinoSqlDialect)
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
            CooperativeExecuteAction(if (variant == 4) -1 else variant, previousAction, PipeExecuteInterceptor(TrinoPipeDialect), variant == 4).actionPerformed(e)
            return e
        }
        override fun close() {
            connection.disconnect()
            notifications.forEach { it.expire() }
            try {
                recording.close()
            } finally {
                dev.brikk.house.sql.intellij.testing.setSqlDialectMapping(project, vf, null)
            }
        }
    }
}

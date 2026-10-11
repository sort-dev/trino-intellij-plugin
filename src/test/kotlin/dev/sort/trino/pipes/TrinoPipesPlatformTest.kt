package dev.sort.trino.pipes

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.*
import dev.sort.trino.sql.TrinoSqlDialect
import dev.sort.trino.validate.TrinoErrorAnnotator

class TrinoPipesPlatformTest : BasePlatformTestCase() {
    override fun tearDown() {
        try { project.service<TrinoPipesSettings>().enabled = false; PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }
        finally { super.tearDown() }
    }
    fun testSettingRefreshAndStatementRanges() {
        val file = myFixture.configureByText("pipeline.sql", "SELECT 1; FROM t |> WHERE x = 2 |> SELECT x; SELECT 3;")
        dev.sort.trino.setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect.INSTANCE)
        project.service<TrinoPipesSettings>().enabled = true
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val psi = PsiDocumentManager.getInstance(project).getPsiFile(myFixture.editor.document)!!
        val statements = PsiTreeUtil.findChildrenOfType(psi, SqlStatement::class.java).filterNot { it.parent is SqlStatement }
        assertEquals(3, statements.size)
        val annotator = TrinoErrorAnnotator()
        assertEmpty(annotator.doAnnotate(annotator.collectInformation(psi))!!.errors)
        project.service<TrinoPipesSettings>().enabled = false
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertFalse(TrinoPipes.isEnabled(project))
    }
    fun testSnapshotRejectsEditAndRevert() {
        myFixture.configureByText("snapshot.sql", "SELECT 1")
        val doc = myFixture.editor.document
        val snapshot = EditorSnapshot.capture(doc)
        WriteCommandAction.runWriteCommandAction(project) { doc.setText("SELECT 2"); doc.setText("SELECT 1") }
        try { snapshot.requireCurrent(); fail("Expected stale snapshot") } catch (_: SqlExecutionRefusal) { }
    }
    fun testLongPipelineDoesNotUseTheOldBoundedLookahead() {
        val text = "SELECT " + List(700) { "1" }.joinToString(" + ") + " AS value |> SELECT value; SELECT 2;"
        val file = myFixture.configureByText("long-pipe.sql", text)
        dev.sort.trino.setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect.INSTANCE)
        project.service<TrinoPipesSettings>().enabled = true
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val psi = PsiDocumentManager.getInstance(project).getPsiFile(myFixture.editor.document)!!
        val statements = PsiTreeUtil.findChildrenOfType(psi, SqlStatement::class.java).filterNot { it.parent is SqlStatement }
        assertEquals(2, statements.size)
        assertTrue(statements.first().text.endsWith("SELECT value"))
    }
    fun testAllThreeDialectOrdersDelegateExactlyOnceAndStopOnClaim() {
        val orders = listOf(listOf(0,1,2), listOf(0,2,1), listOf(1,0,2), listOf(1,2,0), listOf(2,0,1), listOf(2,1,0))
        for (order in orders) for (variant in 1..4) {
            var stockCalls = 0
            val seen = mutableListOf<Int>()
            var target = -1
            val event = AnActionEvent.createFromDataContext("contract", Presentation(), SimpleDataContext.getProjectContext(project))
            var action: AnAction = object : AnAction() { override fun actionPerformed(e: AnActionEvent) { assertSame(event, e); stockCalls++ } }
            for (id in order) action = CooperativeExecuteAction(if (variant == 4) -1 else variant, action, ExecuteInterceptor { e, _ ->
                assertSame(event, e); seen.add(id)
                if (id == target) ExecutionClaim.HANDLED else ExecutionClaim.DECLINED
            }, variant == 4)
            action.actionPerformed(event)
            assertEquals(1, stockCalls)
            assertEquals(order.reversed(), seen)
            target = 1; seen.clear()
            action.actionPerformed(event)
            assertEquals(1, stockCalls)
            assertEquals(1, seen.last())
        }
    }
    fun testPublicRegistrationRestoresOrDetachesWithoutClobberingPeer() {
        val manager = ActionManager.getInstance()
        val originals = CooperativeExecuteConfiguration.IDS.associateWith { manager.getAction(it) }
        val configuration = object : CooperativeExecuteConfiguration() {
            override val interceptor = ExecuteInterceptor { _, _ -> ExecutionClaim.DECLINED }
        }
        configuration.registerActions(manager)
        val wrapper = manager.getAction(CooperativeExecuteConfiguration.IDS.first()) as CooperativeExecuteAction
        val peer = CooperativeExecuteAction(1, wrapper, ExecuteInterceptor { _, _ -> ExecutionClaim.DECLINED })
        manager.replaceAction(CooperativeExecuteConfiguration.IDS.first(), peer)
        try {
            configuration.unregisterActions(manager)
            assertTrue(wrapper.detached)
            assertSame(peer, manager.getAction(CooperativeExecuteConfiguration.IDS.first()))
            CooperativeExecuteConfiguration.IDS.drop(1).forEach { assertSame(originals[it], manager.getAction(it)) }
        } finally { originals.forEach { (id, action) -> manager.replaceAction(id, action!!) } }
    }
    fun testShortcutPromotionUsesThePublicDatabasePromoter() {
        val component = object : javax.swing.JPanel(), com.intellij.ui.PlaceProvider {
            override fun getPlace() = "StructureViewPopup"
        }
        val context = SimpleDataContext.builder().add(PlatformCoreDataKeys.CONTEXT_COMPONENT, component).build()
        val stock = com.intellij.database.actions.RunQueryAction.Alt1()
        val wrapped = CooperativeExecuteAction(1, stock, ExecuteInterceptor { _, _ -> ExecutionClaim.DECLINED })
        val peer = object : AnAction(), ActionWithDelegate<AnAction> {
            override fun getDelegate(): AnAction = wrapped
            override fun actionPerformed(e: AnActionEvent) = wrapped.actionPerformed(e)
        }
        val other = object : AnAction() { override fun actionPerformed(e: AnActionEvent) {} }
        val promoter = CooperativeExecutePromoter()
        assertEquals(listOf(wrapped), promoter.promote(listOf(other, wrapped), context))
        assertEquals(listOf(peer), promoter.promote(listOf(other, peer), context))
        assertEmpty(promoter.promote(listOf(other, stock), context))
        assertEmpty(promoter.promote(listOf(wrapped), DataContext.EMPTY_CONTEXT))
    }
    fun testClaimedCancellationNeverInvokesPredecessor() {
        var delegated = 0
        val previous = object : AnAction() { override fun actionPerformed(e: AnActionEvent) { delegated++ } }
        val wrapper = CooperativeExecuteAction(1, previous, ExecuteInterceptor { _, _ -> throw com.intellij.openapi.progress.ProcessCanceledException() })
        val event = AnActionEvent.createFromDataContext("cancel", Presentation(), SimpleDataContext.getProjectContext(project))
        try { wrapper.actionPerformed(event); fail("Cancellation must propagate") } catch (_: com.intellij.openapi.progress.ProcessCanceledException) { }
        assertEquals(0, delegated)
    }
}

package dev.sort.trino.pipes

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlStatement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.*
import dev.brikk.house.sql.intellij.pipe.isEnabled
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
        val result = annotator.doAnnotate(annotator.collectInformation(psi))!!
        assertEmpty(result.errors)
        assertEmpty(result.pipeErrors)
        project.service<TrinoPipesSettings>().enabled = false
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertFalse(TrinoPipeDialect.isEnabled(project))
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
    fun testSettingKeepsPersistedEnabledOption() {
        assertInstanceOf(project.service<TrinoPipesSettings>().state, ToggleSettings.Options::class.java)
    }
    fun testPipeDiagnosticsUseSharedRangesAlongsideNativeErrors() {
        val sql = "FROM t |> BOGUS 2; SELECT FROM WHERE;"
        val file = myFixture.configureByText("pipe-errors.sql", sql)
        dev.sort.trino.setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect.INSTANCE)
        project.service<TrinoPipesSettings>().enabled = true
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val annotator = TrinoErrorAnnotator()
        val result = annotator.doAnnotate(annotator.collectInformation(myFixture.file))!!
        assertEquals(1, result.errors.size)
        assertEquals(1, result.pipeErrors.size)
        val highlight = myFixture.doHighlighting().single { it.description?.startsWith("Trino PIPE:") == true }
        assertTrue(highlight.toString(), sql.indexOf("BOGUS") in highlight.startOffset until highlight.endOffset)
    }
    fun testWholeStagePhrasesIncludingNestedPipelinesGetKeywordColoring() {
        val sql = "FROM (FROM t |> FULL OUTER JOIN u ON t.id = u.id) AS p |> ORDER BY id"
        val file = myFixture.configureByText("pipe-colors.sql", sql)
        dev.sort.trino.setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect.INSTANCE)
        project.service<TrinoPipesSettings>().enabled = true
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val colors = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == DefaultLanguageHighlighterColors.KEYWORD }
        for (word in listOf("FULL", "OUTER", "JOIN", "ORDER", "BY")) {
            val start = sql.indexOf(word)
            assertTrue("Missing keyword color for $word: $colors", colors.any { it.startOffset <= start && it.endOffset >= start + word.length })
        }
    }
    fun testStagePhrasesAndInputAliasesComplete() {
        dev.sort.trino.setSqlDialectMapping(project, null, TrinoSqlDialect.INSTANCE)
        project.service<TrinoPipesSettings>().enabled = true
        myFixture.configureByText("pipe-keywords.sql", "FROM t |> <caret>")
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings.orEmpty().contains("ORDER BY"))
        assertFalse(myFixture.lookupElementStrings.orEmpty().contains("CURRENT_TIMESTAMP"))
        assertFalse(myFixture.lookupElementStrings.orEmpty().contains("CREATE"))
        com.intellij.codeInsight.lookup.LookupManager.getInstance(project).hideActiveLookup()
        myFixture.configureByText("pipe-columns.sql", "FROM t |> SELECT id AS my_output, id AS my_other |> WHERE my_<caret>")
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings.orEmpty().contains("my_output"))
        assertTrue(myFixture.lookupElementStrings.orEmpty().contains("my_other"))
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
        val configuration = TrinoPipesActionConfiguration()
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
        val promoter = TrinoPipesActionPromoter()
        assertEquals(listOf(wrapped), promoter.promote(listOf(other, wrapped), context))
        assertEquals(listOf(peer), promoter.promote(listOf(other, peer), context))
        assertEmpty(promoter.promote(listOf(other, stock), context))
        assertEmpty(promoter.promote(listOf(wrapped), DataContext.EMPTY_CONTEXT))
    }
}

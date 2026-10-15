package dev.sort.trino.pipes

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.sql.dialects.SqlLanguageDialect
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.EditorPopupContract
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.sort.trino.sql.TrinoSqlDialect

class TrinoPipesEditorPopupTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<TrinoPipesSettings>().enabled = true
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    override fun tearDown() {
        try {
            project.service<TrinoPipesSettings>().enabled = false
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        } finally { super.tearDown() }
    }

    private fun popup() = EditorPopupContract(myFixture, "TrinoPipes.EditorMenu",
        listOf("TrinoPipes.PreviewSql", "TrinoPipes.RunToStage"))

    private fun openSql(sql: String, dialect: SqlLanguageDialect = TrinoSqlDialect) {
        setSqlDialectMapping(project, null, dialect, testRootDisposable)
        myFixture.configureByText("popup.sql", sql)
    }

    private fun openValidPipeContext() = openSql("FROM popup_rows |> SELECT id<caret>")

    fun testRegisteredPopupContextContract() {
        popup().check(unrelated = mapOf("popup-notes.txt" to "SELECT 1", "popup.kt" to "fun ordinary() = 1")) {
            openValidPipeContext()
        }
    }

    fun testOnlyEnabledTrinoPipeShowsThePopup() {
        val popup = popup()
        openValidPipeContext()
        popup.assertShown(popup.context())
        openSql("FROM popup_rows |> SELECT id<caret>", requireNotNull(Language.findLanguageByID("PostgreSQL")) as SqlLanguageDialect)
        popup.assertAbsent(popup.context(), "wrong-dialect SQL")
        openSql("SELECT 1<caret>")
        popup.assertAbsent(popup.context(), "Trino SQL without PIPE")
        project.service<TrinoPipesSettings>().enabled = false
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        openValidPipeContext()
        popup.assertAbsent(popup.context(), "PIPE disabled")
    }
}

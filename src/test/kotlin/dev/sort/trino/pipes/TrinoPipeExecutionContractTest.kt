package dev.sort.trino.pipes

import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.PipeExecutionContract
import dev.sort.trino.setSqlDialectMapping
import dev.sort.trino.sql.TrinoSqlDialect
import java.util.UUID

class TrinoPipeExecutionContractTest : BasePlatformTestCase() {
    private val settings get() = project.service<TrinoPipesSettings>()
    private var previousFlag: String? = null
    private var previousEnabled = false

    override fun setUp() {
        super.setUp()
        previousFlag = System.getProperty(TrinoPipeDialect.vetoProperty)
        previousEnabled = settings.enabled
        System.clearProperty(TrinoPipeDialect.vetoProperty)
        settings.enabled = true
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    override fun tearDown() {
        try {
            settings.enabled = previousEnabled
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            previousFlag.let { flag ->
                if (flag == null) System.clearProperty(TrinoPipeDialect.vetoProperty)
                else System.setProperty(TrinoPipeDialect.vetoProperty, flag)
            }
        } finally {
            super.tearDown()
        }
    }

    private fun contract() = PipeExecutionContract(TrinoPipeDialect, "trino-brikk", "mysql", { settings.enabled = it }) { sql ->
        val file = myFixture.configureByText("pipe-contract-${UUID.randomUUID()}.sql", sql)
        setSqlDialectMapping(project, file.virtualFile, TrinoSqlDialect.INSTANCE)
        myFixture.editor
    }

    fun testDeclinesWhenNotOwned() = contract().declinesWhenNotOwned()
    fun testSelectsStatementsLikeStockExecute() = contract().selectsStatementsLikeStockExecute()
    fun testRefusesWholeScriptsItCannotRun() = contract().refusesWholeScriptsItCannotRun()
    fun testClaimsOnlyPipes() = contract().claimsOnlyPipes()
    fun testRefusesStaleInput() = contract().refusesStaleInput()
    fun testPropagatesCancellation() = contract().propagatesCancellation()
    fun testLowersNestedOnlyPipelines() = contract().lowersNestedOnlyPipelines()
    fun testRunsTopLevelStagesToTheCaret() = contract().runsTopLevelStagesToTheCaret()
    fun testRefusesDestinationChangesDuringPrompting() = contract().refusesDestinationChangesDuringPrompting()
    fun testNotifiesRefusals() = contract().notifiesRefusals()
}

package dev.sort.trino.catalog

import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.ObjectName
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath
import com.intellij.database.util.TreePattern
import com.intellij.database.util.TreePatternUtils
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.sql.dialects.SqlImportUtil
import com.intellij.sql.dialects.SqlResolveMappings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.catalog.DataSourceResolution
import dev.brikk.house.sql.intellij.testing.RecordingConsole
import dev.sort.trino.TrinoDbms

class TrinoDataSourcesTest : BasePlatformTestCase() {
    private val added = mutableListOf<LocalDataSource>()

    override fun setUp() {
        super.setUp()
        myFixture.configureByText("resolve.sql", "SELECT 1")
    }

    override fun tearDown() {
        try {
            SqlResolveMappings.getInstance(project).apply {
                setMapping(myFixture.file.virtualFile, null)
                setMapping(null, null)
            }
            added.forEach(LocalDataSourceManager.getInstance(project)::removeDataSource)
            DbPsiFacade.getInstance(project).clearCaches()
        } catch (failure: Throwable) {
            addSuppressedException(failure)
        } finally {
            super.tearDown()
        }
    }

    fun testConsoleWinsOverScopeAndCompletionCopiesResolve() {
        mapTo(dataSource())
        RecordingConsole(project, myFixture.file.virtualFile, "trino-brikk").use { recording ->
            val source = recording.console.dataSource
            assertEquals(TrinoDbms.TRINO_BRIKK, source.dbms)
            assertEquals(DataSourceResolution.Console(source), trinoDataSourceResolver.resolve(myFixture.file))
            assertEquals(DataSourceResolution.Console(source), trinoDataSourceResolver.resolve(myFixture.file.copy() as com.intellij.psi.PsiFile))
        }
    }

    fun testForeignConsoleDoesNotUseTrinoScopeOrFallbackOrNamespace() {
        mapTo(dataSource())
        RecordingConsole(project, myFixture.file.virtualFile, "postgresql").use { recording ->
            assertEquals(Dbms.POSTGRES, recording.console.dataSource.dbms)
            recording.console.setSearchPath(SearchPath.of(ObjectPath.create("foreign", ObjectKind.DATABASE).append("public", ObjectKind.SCHEMA)))
            assertEquals(DataSourceResolution.NotFound, trinoDataSourceResolver.resolve(myFixture.file))
            assertEquals(emptyList<String>(), trinoDataSourceResolver.consoleNamespace(myFixture.file))
            SqlResolveMappings.getInstance(project).setMapping(myFixture.file.virtualFile, null)
            assertEquals(DataSourceResolution.NotFound, trinoDataSourceResolver.resolve(myFixture.file))
        }
    }

    fun testConsoleNamespaceIsCatalogThenSchema() {
        assertEquals(emptyList<String>(), trinoDataSourceResolver.consoleNamespace(myFixture.file))
        RecordingConsole(project, myFixture.file.virtualFile, "trino-brikk").use { recording ->
            assertEquals(emptyList<String>(), trinoDataSourceResolver.consoleNamespace(myFixture.file))
            recording.console.setSearchPath(SearchPath.of(ObjectPath.create("tpch", ObjectKind.DATABASE).append("tiny", ObjectKind.SCHEMA)))
            assertEquals(listOf("tpch", "tiny"), trinoDataSourceResolver.consoleNamespace(myFixture.file))
        }
    }

    fun testFileScopeSelectsTrinoAndRejectsAmbiguity() {
        val first = dataSource()
        val second = dataSource()
        val foreign = dataSource("postgresql")
        mapTo(second, foreign)
        assertEquals(DataSourceResolution.FileMapping(second), trinoDataSourceResolver.resolve(myFixture.file))
        mapTo(first, second, foreign)
        val resolution = trinoDataSourceResolver.resolve(myFixture.file) as DataSourceResolution.Ambiguous
        assertEquals(setOf(first, second), resolution.candidates.toSet())
    }

    fun testProjectScopeApplies() {
        dataSource()
        val selected = dataSource()
        SqlResolveMappings.getInstance(project).setMapping(null, scopeOf(selected))
        assertEquals(DataSourceResolution.FileMapping(selected), trinoDataSourceResolver.resolve(myFixture.file))
    }

    fun testForeignAndExcludingScopesDoNotUseOnlyTrinoFallback() {
        val source = dataSource()
        mapTo(dataSource("postgresql"))
        assertEquals(DataSourceResolution.NotFound, trinoDataSourceResolver.resolve(myFixture.file))
        SqlResolveMappings.getInstance(project).setMapping(myFixture.file.virtualFile,
            TreePatternUtils.minus(SqlResolveMappings.ALL_DATA_SOURCES, scopeOf(source)))
        assertEquals(DataSourceResolution.NotFound, trinoDataSourceResolver.resolve(myFixture.file))
    }

    fun testOnlyDataSourceFallbackCountsOnlyOurDbms() {
        assertEquals(DataSourceResolution.NotFound, trinoDataSourceResolver.resolve(myFixture.file))
        dataSource("postgresql")
        val source = dataSource()
        assertEquals(DataSourceResolution.OnlyDataSource(source), trinoDataSourceResolver.resolve(myFixture.file))
        val second = dataSource()
        val resolution = trinoDataSourceResolver.resolve(myFixture.file) as DataSourceResolution.Ambiguous
        assertEquals(setOf(source, second), resolution.candidates.toSet())
    }

    fun testStockTrinoIsAcceptedOnlyWhenExplicitlyBound() {
        val stock = dataSource("trino")
        assertEquals("TRINO", stock.dbms.name)
        assertEquals(DataSourceResolution.NotFound, trinoDataSourceResolver.resolve(myFixture.file))
        mapTo(stock)
        assertEquals(DataSourceResolution.FileMapping(stock), trinoDataSourceResolver.resolve(myFixture.file))
        RecordingConsole(project, myFixture.file.virtualFile, "trino").use { recording ->
            assertEquals(DataSourceResolution.Console(recording.console.dataSource), trinoDataSourceResolver.resolve(myFixture.file))
        }
    }

    fun testLookupCancellationPropagates() {
        RecordingConsole(project, myFixture.file.virtualFile, "trino-brikk").use { recording ->
            val cancellation = ProcessCanceledException()
            recording.beforeSessionCall = { throw cancellation }
            for (lookup in listOf<() -> Any>({ trinoDataSourceResolver.resolve(myFixture.file) }, { trinoDataSourceResolver.consoleNamespace(myFixture.file) })) {
                try {
                    lookup()
                    fail("Console lookup must propagate cancellation")
                } catch (actual: ProcessCanceledException) {
                    assertSame(cancellation, actual)
                }
            }
        }
    }

    private fun dataSource(driver: String = "trino-brikk"): LocalDataSource = LocalDataSource().apply {
        name = "Resolver ${added.size}"
        databaseDriver = checkNotNull(DatabaseDriverManager.getInstance().getDriver(driver))
        url = "jdbc:offline:resolver"
        isAutoSynchronize = false
    }.also {
        LocalDataSourceManager.getInstance(project).addDataSource(it)
        DbPsiFacade.getInstance(project).clearCaches()
        added += it
        if (driver == "trino-brikk") assertEquals(TrinoDbms.TRINO_BRIKK, it.dbms)
    }

    private fun scopeOf(vararg sources: LocalDataSource) =
        TreePattern(SqlImportUtil.createDataSources(sources.map { ObjectName.quoted(it.uniqueId) }.toTypedArray()))

    private fun mapTo(vararg sources: LocalDataSource) =
        SqlResolveMappings.getInstance(project).setMapping(myFixture.file.virtualFile, scopeOf(*sources))
}

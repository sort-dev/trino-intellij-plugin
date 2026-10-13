package dev.sort.trino.brikk

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.intellij.*
import dev.brikk.house.sql.intellij.pipe.PipeExecuteInterceptor
import dev.brikk.house.sql.intellij.pipe.PipeText
import dev.brikk.house.sql.intellij.pipe.isEditingEnabled
import dev.sort.trino.pipes.*
import dev.sort.trino.validate.TrinoErrorAnnotator
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

open class BrikkTrinoInjectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        val stdlib = java.io.File(com.intellij.util.PathUtil.getJarPathForClass(kotlin.Unit::class.java))
        com.intellij.testFramework.PsiTestUtil.addLibrary(module, "kotlin-stdlib", stdlib.parent, stdlib.name)
        val jdk = com.intellij.openapi.projectRoots.JavaSdk.getInstance().createJdk("Brikk fixture", System.getProperty("java.home"), false)
        WriteCommandAction.runWriteCommandAction(project) { com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(jdk, testRootDisposable) }
        com.intellij.openapi.roots.ModuleRootModificationUtil.setModuleSdk(module, jdk)
        myFixture.addFileToProject("org/intellij/lang/annotations/Language.java", """
            package org.intellij.lang.annotations;
            public @interface Language { String value(); String prefix() default ""; String suffix() default ""; }
        """.trimIndent())
        myFixture.addFileToProject("runtime/Types.kt", """
            package dev.brikk.house.sql.runtime
            import org.intellij.lang.annotations.Language
            annotation class BrikkSql
            annotation class BrikkSqlDialect(val dialect: String)
            annotation class BrikkTrait
            interface Partial
            interface Shape : Partial
            interface Rel<T>
            object Sql {
                fun trino(@Language("TrinoSQL", prefix = "-- dialect: trino\n") sql: String): Rel<Nothing> = error("compiler")
                fun duckdb(@Language("DuckDBSQL") sql: String): Rel<Nothing> = error("compiler")
            }
        """.trimIndent())
    }
    protected fun configure(body: String): KtStringTemplateExpression {
        myFixture.configureByText("Sample.kt", "import dev.brikk.house.sql.runtime.*\nimport kotlin.text.trimIndent\nimport kotlin.text.trimMargin\n$body")
        val top = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)
        return PsiTreeUtil.findChildrenOfType(top, KtStringTemplateExpression::class.java).last { "SELECT" in it.text || "FROM" in it.text }
    }
    protected fun injected(host: KtStringTemplateExpression): PsiFile {
        val files = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host).orEmpty()
        assertFalse("No injection: ${host.text}", files.isEmpty())
        return files.first().first.containingFile.also { assertEquals("TrinoSQL", it.language.id) }
    }
    fun testScalarBindKeepsKotlinReferenceAndScopedEnablement() {
        val host = configure("@BrikkSql fun query(tenant: String) = Sql.trino(\"SELECT tenant FROM customers WHERE tenant = \$tenant\")")
        val file = injected(host)
        assertTrue(file.text, "tenant = :tenant" in file.text)
        assertEquals(listOf("tenant"), BrikkTrinoProfile.forFile(file)!!.binds)
        assertTrue(TrinoPipeDialect.isEditingEnabled(file))
        assertFalse(project.service<TrinoPipesSettings>().enabled)
        assertEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java))
        val reference = host.entries.mapNotNull { it.expression as? KtNameReferenceExpression }.single()
        assertNotNull(reference.mainReference.resolve())
        RenameProcessor(project, reference.mainReference.resolve()!!, "account", false, false).run()
        assertTrue(myFixture.editor.document.text.contains("\$account"))
    }
    fun testSlotsAndConstantsRemainSeparateFromBinds() {
        val host = configure("""
            const val COLS = "id, tenant"
            @BrikkSql fun query(src: Rel<Nothing>, id: Int) = Sql.trino("FROM ${'$'}src() |> WHERE id > ${'$'}id |> SELECT ${'$'}COLS")
        """.trimIndent())
        val file = injected(host)
        assertTrue(file.text, "FROM src() |> WHERE id > :id |> SELECT id, tenant" in file.text)
        assertEquals(listOf("src"), BrikkTrinoProfile.forFile(file)!!.slots)
        assertEmpty(BrikkTrinoProfile.forFile(file)!!.issues)
    }
    fun testBrikkParseErrorHighlightsTheWholeToken() {
        for (sql in listOf("SELECT 1 FROM", "SELECT \\\"face\\\" FROM")) {
            val file = injected(configure("@BrikkSql fun query() = Sql.trino(\"$sql\")"))
            assertEmpty(BrikkTrinoProfile.forFile(file)!!.issues)
            val source = PipeText.of(file)
            val annotator = TrinoErrorAnnotator()
            val result = annotator.doAnnotate(annotator.collectInformation(file))!!
            assertEquals(1, result.errors.size)
            val range = result.errors.single().first
            assertEquals("FROM", source.text.substring(range.startOffset, range.endOffset))
            val annotations = AnnotationHolderImpl(AnnotationSession(file), false)
            annotations.runAnnotatorWithContext(file, Annotator { _, holder -> annotator.apply(file, result, holder) })
            annotations.assertAllAnnotationsCreated()
            val error = annotations.single { it.severity == HighlightSeverity.ERROR }
            assertEquals(file.text.indexOf("FROM"), error.startOffset)
            assertEquals(file.text.indexOf("FROM") + 4, error.endOffset)
        }
    }
    fun testNativeInjectedPipeDiagnosticsUseUnescapedCoordinates() {
        val settings = project.service<TrinoPipesSettings>()
        val previous = settings.enabled
        settings.enabled = true
        try {
            for (sql in listOf(
                "FROM t |> SELECT \\\"face\\\" AS face\\n |> BOGUS 2",
                "SELECT \\\"face\\\" AS face;\\nFROM t |> BOGUS 2",
            )) {
                val file = injected(configure("fun query() = Sql.trino(\"$sql\")"))
                assertNull(BrikkTrinoProfile.forFile(file))
                val source = PipeText.of(file)
                assertTrue("Expected an escaped injection: ${file.text}", file.text.length > source.text.length)
                val annotator = TrinoErrorAnnotator()
                val info = annotator.collectInformation(file)!!
                assertEquals(source.text, info.source)
                assertEquals(source.text.substringAfter(';').trim(), info.statements.last().second.trim())
                val result = annotator.doAnnotate(info)!!
                assertEmpty(result.errors)
                assertEquals(1, result.pipeErrors.size)
                val annotations = AnnotationHolderImpl(AnnotationSession(file), false)
                annotations.runAnnotatorWithContext(file, Annotator { _, holder -> annotator.apply(file, result, holder) })
                annotations.assertAllAnnotationsCreated()
                val error = annotations.single { it.severity == HighlightSeverity.ERROR }
                assertEquals(file.text.indexOf("BOGUS"), error.startOffset)
                assertEquals(file.text.indexOf("BOGUS") + 5, error.endOffset)
            }
        } finally {
            settings.enabled = previous
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }
    }
    fun testFromFirstInjectionPreservesLaterStatements() {
        for (first in listOf("FROM src()", "FROM src() |> SELECT id")) {
            val file = injected(configure("@BrikkSql fun query(src: Rel<Nothing>) = Sql.trino(\"$first; SELECT 42 AS later; SELECT 43 AS tail\")"))
            val statements = PsiTreeUtil.findChildrenOfType(file, com.intellij.sql.psi.SqlStatement::class.java)
                .filterNot { it.parent is com.intellij.sql.psi.SqlStatement }
            assertTrue(statements.map { it.text }.toString(), statements.any { it.text.trim().removeSuffix(";") == first })
            assertTrue(statements.any { it.text.trim().removeSuffix(";") == "SELECT 42 AS later" })
            assertTrue(statements.any { it.text.trim().removeSuffix(";") == "SELECT 43 AS tail" })
            val selects = PsiTreeUtil.findChildrenOfType(file, com.intellij.sql.psi.SqlSelectStatement::class.java)
            assertTrue(selects.any { it.text.trim().removeSuffix(";") == "SELECT 42 AS later" })
            assertTrue(selects.any { it.text.trim().removeSuffix(";") == "SELECT 43 AS tail" })
        }
    }
    fun testUnknownAndComplexInterpolationAreNotInventedColumns() {
        val host = configure("@BrikkSql fun query(x: Int) = Sql.trino(\"SELECT \${x + 1}, \$missing\")")
        val file = injected(host)
        assertTrue(file.text, "__brikk_unsupported" in file.text)
        assertTrue(file.text, "__brikk_pending" in file.text)
        assertFalse(BrikkTrinoProfile.forFile(file)!!.issues.isEmpty())
    }
    fun testInputColumnsResolveNavigateAndRefreshWithoutSqlEdits() {
        var host = configure("""
            interface Events : Partial { val id: Long; val amount: Double? }
            @BrikkSql fun query(src: Rel<Events>) = Sql.trino("SELECT e.amount FROM ${'$'}src() AS e")
        """.trimIndent())
        var file = injected(host)
        val shape = (BrikkTrinoProfile.contracts(file)["src"] as ShapeResult.Available).shape
        assertEquals(ShapeKnowledge.PARTIAL, shape.knowledge)
        assertEquals(setOf("id", "amount"), shape.columns.map { it.name }.toSet())
        val ref = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java).first { it.text == "e.amount" }
        val resolved = ref.reference.resolve()
        assertNotNull("Expected Kotlin contract resolution", resolved)
        assertEquals("Sample.kt", resolved!!.navigationElement.containingFile.name)
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = myFixture.editor.document
            doc.insertString(doc.text.indexOf("val amount"), "val region: String; ")
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        host = PsiTreeUtil.findChildrenOfType(myFixture.file, KtStringTemplateExpression::class.java).last()
        file = injected(host)
        assertTrue((BrikkTrinoProfile.contracts(file)["src"] as ShapeResult.Available).shape.columns.any { it.name == "region" })
    }
    fun testPipeCompletionAndProjectionVisibility() {
        val host = configure("""
            interface Events : Partial { val id: Long; val amount: Double?; val amount_total: Double }
            @BrikkSql fun query(src: Rel<Events>) = Sql.trino("FROM ${'$'}src() |> WHERE am<caret>")
        """.trimIndent())
        injected(host)
        val variants = myFixture.completeBasic().orEmpty()
        val amount = variants.single { it.lookupString == "amount" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        amount.renderElement(presentation)
        assertEquals("brikk column (src)", presentation.tailText?.trim())
        assertEquals("Double?", presentation.typeText)
        assertEquals(dev.brikk.house.sql.intellij.completion.SqlLookupIcons.BrikkColumn, presentation.icon)
    }
    fun testBrikkColumnCompletionWithProjectPipesOff() {
        assertFalse(project.service<TrinoPipesSettings>().enabled)
        assertBrikkColumnsOffered()
    }
    fun testBrikkColumnCompletionWithPipesVetoed() {
        val old = System.getProperty("trino.pipes")
        try {
            System.setProperty("trino.pipes", "false")
            assertBrikkColumnsOffered()
        } finally {
            if (old == null) System.clearProperty("trino.pipes") else System.setProperty("trino.pipes", old)
        }
    }
    fun testPipesVetoDisablesBrikkEditingWithoutEnablingPreview() {
        val file = injected(configure("@BrikkSql fun query() = Sql.trino(\"FROM t |> SELECT id\")"))
        assertTrue(TrinoPipeDialect.isEditingEnabled(file))
        assertTrue(TrinoPipeDialect.isInspectionHost(file))
        assertFalse(TrinoPipeDialect.enablesPreview(file))
        val previous = System.getProperty(TrinoPipeDialect.vetoProperty)
        try {
            System.setProperty(TrinoPipeDialect.vetoProperty, "false")
            assertFalse(TrinoPipeDialect.isEditingEnabled(file))
        } finally {
            if (previous == null) System.clearProperty(TrinoPipeDialect.vetoProperty)
            else System.setProperty(TrinoPipeDialect.vetoProperty, previous)
        }
    }
    private fun assertBrikkColumnsOffered() {
        val host = configure("""
            interface Events : Partial { val event_id: Long; val event_time: String }
            @BrikkSql fun query(src: Rel<Events>) = Sql.trino("SELECT event_<caret> FROM ${'$'}src()")
        """.trimIndent())
        injected(host)
        val offered = myFixture.completeBasic().orEmpty().map { it.lookupString }.toSet()
        assertTrue(offered.toString(), "event_id" in offered)
        assertTrue(offered.toString(), "event_time" in offered)
    }
    fun testSameNamedCompletionColumnsKeepAliasAndNullability() {
        val host = configure("""
            interface LeftRow : Partial { val id: Long }
            interface RightRow : Partial { val id: Long? }
            @BrikkSql fun query(lefts: Rel<LeftRow>, rights: Rel<RightRow>) = Sql.trino("FROM ${'$'}lefts() AS a |> JOIN ${'$'}rights() AS b ON a.id=b.id |> SELECT i<caret>")
        """.trimIndent())
        injected(host)
        val ids = myFixture.completeBasic().orEmpty().filter { it.lookupString == "id" }
        assertEquals("Both source relations must remain selectable", 2, ids.size)
        val presentations = ids.map { item ->
            com.intellij.codeInsight.lookup.LookupElementPresentation().also { item.renderElement(it) }
        }
        assertEquals(setOf("brikk column (a)" to "Long", "brikk column (b)" to "Long?"),
            presentations.map { it.tailText?.trim() to it.typeText }.toSet())
    }
    fun testInspectionViewCannotRunWithoutAConsole() {
        val file = injected(configure("@BrikkSql fun query(x: Int) = Sql.trino(\"FROM t |> WHERE id = \$x\")"))
        val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext("Brikk guard", com.intellij.openapi.actionSystem.Presentation(),
            com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE, file).build())
        assertEquals(dev.brikk.house.sql.intellij.ExecutionClaim.HANDLED,
            PipeExecuteInterceptor(TrinoPipeDialect).execute(event, com.intellij.database.settings.DatabaseSettings.ExecOption()))
        assertFalse(RunPipesToStageIntention().isAvailable(project, myFixture.editor, file))
        assertFalse(PreviewPipeSqlIntention().isAvailable(project, myFixture.editor, file))
    }
    fun testAnnotationRemovalWithdrawsBrikkProfile() {
        var host = configure("@BrikkSql fun query(x: Int) = Sql.trino(\"SELECT \$x\")")
        assertNotNull(BrikkTrinoProfile.forFile(injected(host)))
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = myFixture.editor.document
            val at = doc.text.indexOf("@BrikkSql ")
            doc.deleteString(at, at + 10)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        host = PsiTreeUtil.findChildrenOfType(myFixture.file, KtStringTemplateExpression::class.java).single()
        assertNull(BrikkTrinoProfile.forFile(injected(host)))
    }
    fun testTrimIndentAndMarginKeepBoundReferences() {
        val quotes = "\"\"\""
        for (trim in listOf("trimIndent", "trimMargin")) {
            val marker = if (trim == "trimMargin") "|" else ""
            val host = configure("@BrikkSql fun query(x: Int) = Sql.trino($quotes\n    ${marker}SELECT \$x AS value\n    $quotes.$trim())")
            val file = injected(host)
            val text = InjectedLanguageManager.getInstance(project).getUnescapedText(file)
            assertTrue(text, "SELECT :x AS value" in text)
            assertTrue(text, text.trimEnd().endsWith("AS value"))
        }
    }
    fun testPhysicalDdlInspectionsKeepColumnsAndBindsDistinct() {
        val schema = myFixture.addFileToProject("schema.sql", "CREATE TABLE customers (tenant VARCHAR NOT NULL, id INTEGER);")
        dev.brikk.house.sql.intellij.testing.setSqlDialectMapping(project, schema.virtualFile, dev.sort.trino.sql.TrinoSqlDialect)
        val manager = com.intellij.sql.database.SqlDataSourceManager.getInstance(project)
        val source = manager.createEmpty().apply {
            name = "Brikk Trino DDL fixture"
            definedDbms = dev.sort.trino.TrinoDbms.TRINO_BRIKK
            setFiles(arrayOf(schema.virtualFile))
        }
        manager.addDataSource(source)
        try {
            val sync = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                source.sync()
                // 263 sync() computes synchronously and removes waitComputed().
                source.javaClass.methods.firstOrNull { it.name == "waitComputed" && it.parameterCount == 0 }?.invoke(source)
            }
            com.intellij.testFramework.PlatformTestUtil.waitForFuture(sync)
            com.intellij.database.psi.DbPsiFacade.getInstance(project).clearCaches()
            com.intellij.sql.dialects.SqlResolveMappings.getInstance(project).setMapping(null, com.intellij.sql.dialects.SqlResolveMappings.ALL_DATA_SOURCES)
            val registered = com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools().associateBy { it.shortName }
            val inspections = listOf("SqlConstantExpressionInspection", "SqlResolveInspection").map {
                requireNotNull(registered[it]) { "Missing registered inspection: $it; ${registered.keys.filter { name -> name.startsWith("Sql") }}" }.tool
            }
            myFixture.enableInspections(*inspections.toTypedArray())
            var host = configure("@BrikkSql fun query(tenant: String) = Sql.trino(\"SELECT tenant FROM customers WHERE tenant = \$tenant\")")
            val sql = injected(host)
            assertTrue(com.intellij.sql.dialects.SqlDataSourceMappings.getInstance(project).getDataSources(sql).isNotEmpty())
            val binds = myFixture.doHighlighting().mapNotNull { it.description }
            assertFalse(binds.toString(), binds.any { it.contains("always", true) && it.contains("true", true) })
            host = configure("@BrikkSql fun query() = Sql.trino(\"SELECT tenant FROM customers WHERE tenant = tenant\")")
            injected(host)
            val columns = myFixture.doHighlighting().mapNotNull { it.description }
            assertTrue(columns.toString(), columns.any { it.contains("always", true) && it.contains("true", true) })
        } finally { manager.removeDataSource(source) }
    }
}

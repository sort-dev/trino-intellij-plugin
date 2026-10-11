package dev.sort.trino.brikk

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlReferenceExpression
import dev.brikk.house.intellij.*
import org.jetbrains.kotlin.psi.KtCallExpression
import java.nio.file.Path

/** Opt-in, genuine compiler-generated output; not handwritten generated traits. */
class BrikkCompilerRelationTest : BrikkTrinoInjectionTest() {
    private fun enableCompiler() {
        val jar = Path.of(System.getProperty("test.brikkCompilerPlugin") ?: error("Missing reviewed compiler fixture"))
        assertEquals("2.4.20-ij262-34", org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION)
        val stamp = java.util.zip.ZipFile(jar.toFile()).use { zip ->
            java.util.Properties().apply { zip.getInputStream(zip.getEntry("META-INF/brikk-engine-kotlin-compiler-plugin.build")!!).use(::load) }
        }
        assertTrue(stamp.toString(), stamp.values.any { it == "2.4.20-ij262-34" })
        val ep = com.intellij.openapi.extensions.ExtensionPointName.create<org.jetbrains.kotlin.idea.fir.extensions.KotlinBundledFirCompilerPluginProvider>(
            "org.jetbrains.kotlin.bundledFirCompilerPluginProvider")
        ep.point.registerExtension(object : org.jetbrains.kotlin.idea.fir.extensions.KotlinBundledFirCompilerPluginProvider {
            override fun provideBundledPluginJar(project: com.intellij.openapi.project.Project, userSuppliedPluginJar: Path): Path? =
                jar.takeIf { it == userSuppliedPluginJar }
        }, testRootDisposable)
        val runtime = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("runtime/Types.kt"))!!
        val document = PsiDocumentManager.getInstance(project).getDocument(runtime)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.replace("interface Rel<T>", "class Rel<out T : Partial>")
                .replace("fun trino", "@BrikkSqlDialect(\"trino\") fun trino"))
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val manager = com.intellij.facet.FacetManager.getInstance(module)
            val existing = org.jetbrains.kotlin.idea.facet.KotlinFacet.get(module)
            val facet = existing ?: manager.createFacet(org.jetbrains.kotlin.idea.facet.KotlinFacetType.INSTANCE, "Kotlin", null)
            facet.configuration.settings.useProjectSettings = false
            facet.configuration.settings.compilerArguments = org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments().apply {
                pluginClasspaths = arrayOf(jar.toString())
            }
            if (existing == null) manager.createModifiableModel().apply { addFacet(facet); commit() }
        }
    }
    fun testActualGeneratedInputResolvesOnColdOpen() {
        enableCompiler()
        val host = configure("""
            @BrikkSql fun source() = Sql.trino("SELECT 1 AS id, 'x' AS code")
            @BrikkSql fun query(src: Rel<SourceOut>) = Sql.trino("SELECT r.code FROM ${'$'}src() AS r")
        """.trimIndent())
        val file = injected(host)
        val result = BrikkTrinoProfile.contracts(file)["src"]
        assertTrue(result.toString(), result is ShapeResult.Available)
        val shape = (result as ShapeResult.Available).shape
        assertEquals(ShapeKnowledge.FULL, shape.knowledge)
        assertEquals(setOf("id", "code"), shape.columns.map { it.name }.toSet())
        val reference = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java).first { it.text == "r.code" }
        assertNotNull(reference.reference.resolve())
        assertEquals(NavigationKind.RELATION_FALLBACK, shape.columns.single { it.name == "code" }.navigation!!.kind)
    }
    fun testActualRefinedCallAndDeclaredGenericBounds() {
        enableCompiler()
        val host = configure("""
            @BrikkTrait interface HasId : Partial { val id: Int }
            @BrikkSql fun source() = Sql.trino("SELECT 1 AS id, 'x' AS code")
            @BrikkSql fun <T : HasId> extend(src: Rel<T>) = Sql.trino("FROM ${'$'}src() |> EXTEND 2 AS added")
            fun use() { val refined = extend(source()); println(refined) }
        """.trimIndent())
        val generic = (BrikkTrinoProfile.contracts(injected(host))["src"] as ShapeResult.Available).shape
        assertEquals(setOf("id"), generic.columns.map { it.name }.toSet())
        assertEquals(ShapeKnowledge.PARTIAL, generic.knowledge)
        val expression = PsiTreeUtil.findChildrenOfType(myFixture.file, KtCallExpression::class.java).single { it.calleeExpression?.text == "extend" }
        val refined = BrikkRelationShapes.ofExpression(expression)
        assertTrue(refined.toString(), refined is ShapeResult.Available)
        assertEquals(setOf("id", "code", "added"), (refined as ShapeResult.Available).shape.columns.map { it.name }.toSet())
    }
}

package dev.sort.trino.sql

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * The CENSUS scoreboard, green-LOCKED: every statement of every family in corpus/census/
 * (harvested from Trino's own repo at tag 483 by `./gradlew harvestCensus`, graded by the bundled
 * trino-parser — see TrinoCensusHarvest) must parse on OUR substrate with zero PsiErrorElements.
 *
 * Census file format: blocks of `-- @stmt <origin>` header + statement text terminated by `;`.
 * Each statement is parsed as its own file (exact error attribution), and the whole family file
 * is parsed once more as one multi-statement file (cross-statement boundary leakage check).
 *
 * GREEN-LOCK DISCIPLINE: a family that is green must stay green — any red here is a regression
 * and fails the build. Families that measurably cannot go green on the PG substrate are listed in
 * [DEGRADED] with the precise blocking shape; a degraded family that STARTS passing also fails
 * (stale entry — promote it to the lock).
 */
class TrinoCensusScoreboardTest : BasePlatformTestCase() {

    private var counter = 0

    private data class Verdict(val errors: Int, val first: String?, val around: String?)

    private fun verdict(sql: String): Verdict {
        val psi = myFixture.configureByText("c${counter++}.sql", sql)
        SqlDialectMappings.getInstance(project).setMapping(psi.virtualFile, TrinoSqlDialect.INSTANCE)
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(psi.virtualFile)!!
        val errs = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        val first = errs.firstOrNull()
        val around = first?.let {
            val t = file.text
            val s = (it.textRange.startOffset - 25).coerceAtLeast(0)
            t.substring(s, (it.textRange.startOffset + 25).coerceAtMost(t.length)).replace("\n", "⏎")
        }
        return Verdict(errs.size, first?.errorDescription?.take(70), around)
    }

    /** `-- @stmt <origin>` blocks → (origin, statementText). */
    private fun statementsOf(file: File): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var origin: String? = null
        val sql = StringBuilder()
        fun flush() {
            val o = origin
            if (o != null && sql.isNotBlank()) out.add(o to sql.toString().trim())
            sql.setLength(0)
        }
        file.readLines().forEach { line ->
            if (line.startsWith("-- @stmt ")) {
                flush()
                origin = line.removePrefix("-- @stmt ").trim()
            } else {
                sql.append(line).append('\n')
            }
        }
        flush()
        return out
    }

    fun testCensusGreenLocked() {
        val dir = File(System.getProperty("corpus.dir"), "census")
        val files = dir.listFiles { f -> f.extension == "sql" }?.sortedBy { it.name }.orEmpty()
        assertTrue("census corpus missing — run ./gradlew harvestCensus and commit", files.isNotEmpty())

        var green = 0
        val reds = ArrayList<Triple<String, String, Verdict>>() // family, origin, verdict
        val greenFamilies = ArrayList<String>()
        for (f in files) {
            val family = f.nameWithoutExtension
            var familyGreen = true
            for ((origin, sql) in statementsOf(f)) {
                val v = verdict(sql)
                if (v.errors > 0) {
                    familyGreen = false
                    reds.add(Triple(family, origin, v))
                }
            }
            // cross-statement leakage: the family file as ONE multi-statement document
            if (familyGreen) {
                val v = verdict(f.readText())
                if (v.errors > 0) {
                    familyGreen = false
                    reds.add(Triple(family, "<whole-family file>", v))
                }
            }
            if (familyGreen) {
                green++
                greenFamilies.add(family)
            }
        }

        val redFamilies = reds.map { it.first }.distinct()
        val board = StringBuilder("\n=== Trino census scoreboard (150-family sample, trino-parser-graded) ===\n")
        board.append("families green: $green/${files.size}\n")
        for ((family, origin, v) in reds) {
            board.append("  red  $family  (${v.errors})  [${v.first}]  «${v.around}»  @$origin\n")
        }
        board.append("=======================================================================")
        println(board)

        val unexpectedReds = redFamilies.filterNot { it in DEGRADED }
        val staleDegraded = DEGRADED.keys.filter { it in greenFamilies }
        assertTrue(
            "GREEN-LOCK violated — red families not in the documented degraded set: $unexpectedReds (see board above)",
            unexpectedReds.isEmpty(),
        )
        assertTrue(
            "stale DEGRADED entries (now green — promote to the lock): $staleDegraded",
            staleDegraded.isEmpty(),
        )
    }

    private companion object {
        /**
         * Families measured red that stay documented-degraded (duckdb precedent: ≤ a handful,
         * each with the exact blocking shape). Currently EMPTY — the whole census is green.
         */
        val DEGRADED: Map<String, String> = emptyMap()
    }
}

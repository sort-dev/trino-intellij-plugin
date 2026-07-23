package dev.sort.trino.tools

import io.trino.sql.ReservedIdentifiers
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate

/**
 * Bundled function/keyword catalog harvester (PLAN Stage 3, the "not connected" source).
 *
 * Two independent authorities feed the two committed resources:
 *
 *  1. **functions.tsv** — from a LIVE Trino over JDBC (`SHOW FUNCTIONS`). Trino self-describes its
 *     whole function surface; zero hand maintenance. Each row is `name<TAB>kind<TAB>return_type`,
 *     deduped by (name, kind), deterministic (the return_type kept is the lexicographic min across
 *     that name+kind's signatures, so re-runs against the same engine are byte-identical regardless
 *     of ResultSet row order), identifier-named only, name-then-kind sorted.
 *
 *     `SHOW FUNCTIONS` (verified live against Trino 483, 2026-07-23) returns SIX columns with
 *     DISPLAY labels (spaces + title case), in this order:
 *         1 "Function"        2 "Return Type"   3 "Argument Types"
 *         4 "Function Type"   5 "Deterministic" 6 "Description"
 *     We resolve name / return-type / function-type by NORMALIZED label (lowercased, spaces
 *     stripped) with positional fallback, so a future rename to snake_case (`function_name`,
 *     `function_type`, ...) keeps working. `kind` is the lowercased "Function Type"
 *     (scalar | aggregate | window | table).
 *
 *  2. **keywords.txt** — from the BUNDLED parser, not the wire: `io.trino.sql.ReservedIdentifiers
 *     .reservedIdentifiers()` (io.trino:trino-parser, pinned to the engine version in
 *     build.gradle.kts). This is the CLEANEST programmatic reserved-word source — one public API
 *     call, engine-versioned, no reflection, already free of operator/punctuation tokens. It returns
 *     the RESERVED set (words that must be double-quoted to serve as identifiers: SELECT, FROM,
 *     WHERE, GROUP, UNNEST, ...). The alternative surface — the ANTLR lexer's literal vocabulary,
 *     `io.trino.grammar.sql.SqlBaseLexer.VOCABULARY` (~313 literal keywords, incl. NON-reserved ones
 *     like LIMIT / OVER / LATERAL) — is richer but requires reflection over the token table and
 *     filtering the ~31 symbolic literals; reserved is the deliberate, defensible choice for CAPS
 *     keyword completion.
 *
 * Header comments (`#`-prefixed; the loader skips them) carry engine version, harvest date, and
 * counts. Regenerate on every trino-jdbc / trino-parser bump and commit the diff:
 *   ./gradlew harvestFunctionCatalog -Ptrino.harvest.url='jdbc:trino://localhost:18080?sessionUser=harvest'
 */
fun main(args: Array<String>) {
    val url = args.getOrElse(0) { "jdbc:trino://localhost:18080?sessionUser=harvest" }
    val outDir = File(args.getOrElse(1) { "src/main/resources/trino" }).apply { mkdirs() }
    val today = LocalDate.now().toString()

    DriverManager.getConnection(url).use { conn ->
        val engineVersion = engineVersion(conn)

        // (name, kind) -> return_type, keeping the lexicographic-min return_type for determinism.
        val fns = sortedMapOf<Pair<String, String>, String>(
            compareBy({ it.first }, { it.second }),
        )
        var rawRows = 0
        conn.createStatement().use { st ->
            st.executeQuery("SHOW FUNCTIONS").use { rs ->
                val meta = rs.metaData
                val byName = (1..meta.columnCount).associateBy {
                    meta.getColumnLabel(it).lowercase().replace(" ", "")
                }
                // Normalized-label resolution with positional fallback (see the header note).
                val nameCol = byName["function"] ?: byName["functionname"] ?: 1
                val retCol = byName["returntype"] ?: 2
                val typeCol = byName["functiontype"] ?: 4
                while (rs.next()) {
                    rawRows++
                    val name = rs.getString(nameCol)?.trim().orEmpty()
                    if (!isCompletableName(name)) continue
                    val kind = rs.getString(typeCol)?.trim()?.lowercase().orEmpty().ifEmpty { "other" }
                    val ret = rs.getString(retCol)?.trim().orEmpty().ifEmpty { "?" }
                    fns.merge(name to kind, ret) { a, b -> if (a <= b) a else b }
                }
            }
        }

        val byKind = fns.keys.groupingBy { it.second }.eachCount().toSortedMap()
        val kindSummary = byKind.entries.joinToString(" ") { (k, n) -> "$k=$n" }
        val distinctNames = fns.keys.mapTo(HashSet()) { it.first }.size

        File(outDir, "functions.tsv").writeText(
            buildString {
                appendLine("# Trino function catalog — SHOW FUNCTIONS over a live coordinator.")
                appendLine("# Regenerate: ./gradlew harvestFunctionCatalog -Ptrino.harvest.url=...  (then commit)")
                appendLine("# engine: Trino $engineVersion | harvested: $today")
                appendLine("# rows: ${fns.size} (${kindSummary.ifEmpty { "none" }}) | distinct names: $distinctNames | raw SHOW FUNCTIONS rows: $rawRows")
                appendLine("# format: name<TAB>kind<TAB>return_type ; kind = lowercased \"Function Type\"; deduped by (name,kind), min return_type; sorted.")
                fns.forEach { (key, ret) -> appendLine("${key.first}\t${key.second}\t$ret") }
            },
        )

        val reserved = ReservedIdentifiers.reservedIdentifiers().map { it.uppercase() }.toSortedSet()
        File(outDir, "keywords.txt").writeText(
            buildString {
                appendLine("# Trino reserved words — io.trino.sql.ReservedIdentifiers.reservedIdentifiers()")
                appendLine("# source: BUNDLED io.trino:trino-parser (pinned to the engine in build.gradle.kts); NOT the wire.")
                appendLine("# engine: Trino $engineVersion | harvested: $today | count: ${reserved.size}")
                appendLine("# NOTE: RESERVED set only (words needing quoting to be identifiers). The fuller lexer literal")
                appendLine("#   vocabulary (io.trino.grammar.sql.SqlBaseLexer.VOCABULARY, ~313 incl. non-reserved LIMIT/OVER)")
                appendLine("#   is the alternative surface but needs reflection + symbolic-token filtering.")
                reserved.forEach { appendLine(it) }
            },
        )

        println("catalog: ${fns.size} function rows ($kindSummary), $distinctNames names, ${reserved.size} reserved words")
        println("  engine=$engineVersion  raw SHOW FUNCTIONS rows=$rawRows  ->  ${outDir.absolutePath}")
    }
}

/** Identifier-named only (parity with the loader's filter): first char letter or underscore. */
private fun isCompletableName(name: String): Boolean =
    name.isNotEmpty() && (name.first().isLetter() || name.first() == '_')

private fun engineVersion(conn: Connection): String =
    runCatching {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT version()").use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }.getOrNull() ?: conn.metaData.databaseProductVersion ?: "unknown"

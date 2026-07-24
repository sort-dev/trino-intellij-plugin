package dev.sort.trino.tools

import io.trino.sql.parser.SqlParser
import io.trino.sql.tree.Cast
import io.trino.sql.tree.Node
import io.trino.sql.tree.Statement
import java.io.File

/**
 * Census harvester: samples real-usage Trino SQL from Trino's OWN repo (tag 483) into a committed
 * syntax-census corpus, graded by Trino's OWN parser (io.trino:trino-parser:483 — the exact
 * bundled authority, no server needed).
 *
 * Sources (real-usage SQL, per the duckdb census methodology):
 *  - every .sql file under `testing/trino-product-tests` — product-test files. Two shapes: plain
 *    .sql (tpch/tpcds queries) and `--!`-sectioned files (`--! name: x` starts a SQL section, a
 *    bare `--!` ends it and starts expected results, which are skipped).
 *  - every .md file under `docs/src/main/sphinx` — every ```sql fenced block. Docs fences carry the exotic
 *    syntax (MATCH_RECOGNIZE, JSON_TABLE, MERGE, routines, GRANT/DENY) but also clause fragments;
 *    the grader drops fragments automatically.
 *
 * Pipeline: chunk → quote/comment-aware split on `;` → grade with SqlParser.createStatement
 * (ParsingException ⇒ not a Trino statement ⇒ drop — this is the exact-authority filter) →
 * normalize/dedupe → bucket into syntax families → sample up to [PER_FAMILY] per family →
 * write `corpus/census/<family>.sql`.
 *
 * FAMILY = AST statement-class head + most-salient feature (priority-ordered walk of
 * [Node.getChildren]): `query-lambda`, `create_table-row_type`, `merge`, `show_stats`, ... — the
 * AST gives principled bucketing (no regex guessing), and feature priority keeps the exotic
 * constructs (match_recognize, json_table, table_fn, lambdas) from drowning in plain SELECTs.
 *
 * CENSUS FILE FORMAT (the scoreboard test parses this): per statement, one header line
 * `-- @stmt <origin>` followed by the statement text terminated by `;`, blocks separated by a
 * blank line. Deterministic: same input tree → same output (stable file order, first-N sampling).
 *
 * Usage: TrinoCensusHarvest <trinoCheckout> <censusOutDir>
 */
fun main(args: Array<String>) {
    val checkout = File(args[0])
    val outDir = File(args[1])
    val productTests = File(checkout, "testing/trino-product-tests")
    val docs = File(checkout, "docs/src/main/sphinx")
    require(productTests.isDirectory) { "not a trino checkout (no testing/trino-product-tests): $checkout" }
    require(docs.isDirectory) { "not a trino checkout (no docs/src/main/sphinx): $checkout" }

    val parser = SqlParser()
    val stats = Stats()
    // family -> (normalizedKey -> (sql, origin)); LinkedHashMap keeps deterministic insertion order
    val perFamily = LinkedHashMap<String, LinkedHashMap<String, Pair<String, String>>>()

    fun offer(sqlRaw: String, origin: String, source: String) {
        val sql = sqlRaw.trim().removeSuffix(";").trim()
        if (sql.length < 8 || sql.length > 4000) return
        stats.candidates++
        val statement: Statement = try {
            parser.createStatement(sql)
        } catch (_: Exception) { // ParsingException = not (whole-statement) Trino SQL; fragments land here
            stats.dropped++
            return
        }
        stats.valid++
        when (source) {
            "docs" -> stats.validDocs++
            "parser-tests" -> stats.validParserTests++
            else -> stats.validTests++
        }
        val family = familyOf(statement)
        val bucket = perFamily.getOrPut(family) { LinkedHashMap() }
        if (bucket.size >= PER_FAMILY * OVERSAMPLE) return
        val key = sql.lowercase().replace(WS, " ")
        if (!bucket.containsKey(key)) bucket[key] = sql to origin
    }

    // Whole-chunk-first: a chunk that parses as ONE statement stays unsplit — SQL routines
    // (CREATE FUNCTION ... BEGIN ... END) carry `;` INSIDE the body, so splitting first would
    // chop every routine and no body-bearing statement could ever survive grading.
    fun offerChunk(chunk: String, rel: String, startLine: Int, source: String) {
        val whole = chunk.trim().removeSuffix(";").trim()
        val wholeParses = whole.length in 8..4000 && runCatching { parser.createStatement(whole) }.isSuccess
        if (wholeParses) {
            offer(whole, "$rel:$startLine", source)
            return
        }
        // The udf/sql docs show inline-UDF bodies as standalone `FUNCTION ...` fragments — the
        // documented WITH-FUNCTION component (LOOP/REPEAT/CASE/DECLARE routine syntax lives ONLY
        // there). Offer the fragment verbatim inside the minimal legal host statement; grading
        // still decides, and the origin is marked `(wrapped)`.
        if (whole.length in 8..4000 && Regex("^FUNCTION\\b", RegexOption.IGNORE_CASE).containsMatchIn(whole)) {
            val wrapped = "WITH\n$whole\nSELECT 1"
            if (runCatching { parser.createStatement(wrapped) }.isSuccess) {
                offer(wrapped, "$rel:$startLine (wrapped)", source)
                return
            }
        }
        for ((offset, stmt) in splitStatements(chunk)) {
            offer(stmt, "$rel:${startLine + offset}", source)
        }
    }

    // --- product-tests: plain + `--!`-sectioned .sql files ---
    productTests.walkTopDown().filter { it.isFile && it.extension == "sql" }.sortedBy { it.path }.forEach { f ->
        stats.testFiles++
        val rel = f.relativeTo(checkout).path
        for ((startLine, chunk) in sqlChunksOfProductTest(f.readText())) {
            offerChunk(chunk, rel, startLine, "tests")
        }
    }

    // --- docs: ```sql fences in the sphinx markdown ---
    docs.walkTopDown().filter { it.isFile && it.extension == "md" }.sortedBy { it.path }.forEach { f ->
        val rel = f.relativeTo(checkout).path
        val fences = sqlFencesOf(f.readText())
        if (fences.isNotEmpty()) stats.docFiles++
        for ((startLine, fence) in fences) {
            offerChunk(fence, rel, startLine, "docs")
        }
    }

    // --- parser-internal fallback, offered LAST so real-usage SQL keeps bucket precedence:
    // Java string literals + text blocks from trino-parser's own tests. Fills heads the docs only
    // show as synopsis templates (SET ROLE, SHOW STATS, SHOW ROLE GRANTS, routine bodies, ...) —
    // still graded by the parser, so only genuine statements survive.
    val parserTests = File(checkout, "core/trino-parser/src/test/java/io/trino/sql/parser")
    if (parserTests.isDirectory) {
        parserTests.walkTopDown().filter { it.isFile && it.extension == "java" }.sortedBy { it.path }.forEach { f ->
            stats.parserTestFiles++
            val rel = f.relativeTo(checkout).path
            for ((line, literal) in javaStringsOf(f.readText())) {
                offerChunk(literal, rel, line, "parser-tests")
            }
        }
    }

    // --- write the census ---
    outDir.deleteRecursively()
    outDir.mkdirs()
    var statements = 0
    for ((family, bucket) in perFamily.toSortedMap()) {
        val sample = bucket.values.take(PER_FAMILY)
        if (sample.isEmpty()) continue
        statements += sample.size
        File(outDir, "$family.sql").writeText(
            sample.joinToString("\n\n") { (sql, origin) -> "-- @stmt $origin\n$sql;" } + "\n",
        )
    }

    println(
        "census: ${stats.testFiles} product-test files + ${stats.docFiles} docs files with sql fences " +
            "+ ${stats.parserTestFiles} parser-test files, " +
            "${stats.candidates} candidates, ${stats.valid} trino-parser-valid " +
            "(${stats.validTests} tests / ${stats.validDocs} docs / ${stats.validParserTests} parser-tests), " +
            "${stats.dropped} dropped, ${perFamily.size} families, $statements statements sampled",
    )
}

private class Stats {
    var testFiles = 0
    var docFiles = 0
    var parserTestFiles = 0
    var candidates = 0
    var valid = 0
    var validTests = 0
    var validDocs = 0
    var validParserTests = 0
    var dropped = 0
}

// --- source chunking -------------------------------------------------------------------------

/**
 * Product-test sectioning: yields (1-based startLine, sqlText) chunks. `--! name: x` opens a SQL
 * section, any other `--!` line closes it (expected results follow, skipped). Files without any
 * `--!` marker are one whole-file chunk.
 */
private fun sqlChunksOfProductTest(text: String): List<Pair<Int, String>> {
    if (!text.contains("--!")) return listOf(1 to text)
    val chunks = ArrayList<Pair<Int, String>>()
    var collecting = false
    var start = 0
    val current = StringBuilder()
    text.lineSequence().forEachIndexed { i, line ->
        val t = line.trim()
        when {
            t.startsWith("--!") && t.removePrefix("--!").trim().startsWith("name:") -> {
                if (collecting && current.isNotBlank()) chunks.add(start to current.toString())
                current.setLength(0)
                collecting = true
                start = i + 2 // SQL starts on the line after the marker (1-based)
            }
            t.startsWith("--!") -> {
                if (collecting && current.isNotBlank()) chunks.add(start to current.toString())
                current.setLength(0)
                collecting = false
            }
            collecting -> current.append(line).append('\n')
        }
    }
    if (collecting && current.isNotBlank()) chunks.add(start to current.toString())
    return chunks
}

/**
 * Candidate code fences: yields (1-based startLine, fenceBody). The docs convention puts full SQL
 * examples in ```sql AND bare ``` fences (functions/, udf/, sql/ pages), with query output in
 * ```text — but some text/none fences carry SQL too, and the trino-parser grading step drops
 * whatever isn't a statement, so all four kinds are offered. Explicitly non-SQL languages
 * (properties/java/json/shell/xml/yaml/bash) are skipped outright.
 */
private fun sqlFencesOf(text: String): List<Pair<Int, String>> {
    val fences = ArrayList<Pair<Int, String>>()
    var inFence = false
    var start = 0
    val current = StringBuilder()
    text.lineSequence().forEachIndexed { i, line ->
        val t = line.trim()
        when {
            !inFence && t.startsWith("```") -> {
                val lang = t.trimStart('`').trim()
                if (lang in CANDIDATE_FENCE_LANGS) {
                    inFence = true
                    start = i + 2
                    current.setLength(0)
                }
                // non-candidate language: body ignored until its closing fence — treat the fence
                // pair symmetrically by entering a "skip" fence
                else {
                    inFence = true
                    start = -1
                    current.setLength(0)
                }
            }
            inFence && t.trimEnd('`').isEmpty() && t.startsWith("```") -> {
                if (start > 0 && current.isNotBlank()) fences.add(start to current.toString())
                inFence = false
            }
            inFence && start > 0 -> current.append(line).append('\n')
        }
    }
    return fences
}

private val CANDIDATE_FENCE_LANGS = setOf("sql", "", "text", "none")

/**
 * Java string literals + text blocks from a .java source: yields (1-based line, unescaped value).
 * Comments and char literals are skipped; text blocks get a trimIndent approximation of Java's
 * incidental-whitespace stripping. Extraction is approximate by design — every value is graded by
 * the trino-parser afterwards, so a mis-extracted fragment simply drops.
 */
private fun javaStringsOf(text: String): List<Pair<Int, String>> {
    val out = ArrayList<Pair<Int, String>>()
    var i = 0
    var line = 1
    val n = text.length
    fun unescape(s: String): String {
        val b = StringBuilder(s.length)
        var j = 0
        while (j < s.length) {
            val c = s[j]
            if (c == '\\' && j + 1 < s.length) {
                when (val e = s[j + 1]) {
                    'n' -> b.append('\n')
                    't' -> b.append('\t')
                    'r' -> b.append('\r')
                    '"' -> b.append('"')
                    '\'' -> b.append('\'')
                    '\\' -> b.append('\\')
                    's' -> b.append(' ')
                    'u' -> {
                        if (j + 5 < s.length) {
                            s.substring(j + 2, j + 6).toIntOrNull(16)?.let { b.append(it.toChar()) }
                            j += 4
                        }
                    }
                    else -> b.append(e)
                }
                j += 2
            } else {
                b.append(c)
                j++
            }
        }
        return b.toString()
    }
    while (i < n) {
        val c = text[i]
        when {
            c == '\n' -> { line++; i++ }
            c == '/' && i + 1 < n && text[i + 1] == '/' -> { while (i < n && text[i] != '\n') i++ }
            c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                i += 2
                while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) { if (text[i] == '\n') line++; i++ }
                i += 2
            }
            c == '\'' -> { // char literal
                i++
                if (i < n && text[i] == '\\') i++
                i++
                if (i < n && text[i] == '\'') i++
            }
            c == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> { // text block
                val startLine = line
                i += 3
                val start = i
                while (i + 2 < n && !(text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"')) {
                    if (text[i] == '\n') line++
                    i++
                }
                out.add(startLine to unescape(text.substring(start, i)).trimIndent())
                i += 3
            }
            c == '"' -> {
                val startLine = line
                i++
                val start = i
                while (i < n && text[i] != '"') {
                    if (text[i] == '\\') i++
                    i++
                }
                out.add(startLine to unescape(text.substring(start, i)))
                i++
            }
            else -> i++
        }
    }
    return out
}

/**
 * Split a chunk into `;`-terminated statements, aware of `'...'` strings ('' escape), `"..."`
 * identifiers, `--` line comments, and block comments. Yields (0-based line offset within chunk,
 * statement text). A trailing unterminated segment is still a candidate (product-test sections
 * often omit the final `;`).
 */
private fun splitStatements(chunk: String): List<Pair<Int, String>> {
    val out = ArrayList<Pair<Int, String>>()
    var i = 0
    var stmtStart = 0
    var line = 0
    var stmtLine = 0
    val n = chunk.length
    while (i < n) {
        val c = chunk[i]
        when {
            c == '\n' -> { line++; i++ }
            c == '$' && i + 1 < n && chunk[i + 1] == '$' -> { // $$-quoted body (LANGUAGE UDFs)
                i += 2
                while (i + 1 < n && !(chunk[i] == '$' && chunk[i + 1] == '$')) { if (chunk[i] == '\n') line++; i++ }
                i += 2
            }
            c == '\'' -> { // string literal, '' escapes
                i++
                while (i < n) {
                    if (chunk[i] == '\n') line++
                    if (chunk[i] == '\'') {
                        if (i + 1 < n && chunk[i + 1] == '\'') i++ else break
                    }
                    i++
                }
                i++
            }
            c == '"' -> {
                i++
                while (i < n && chunk[i] != '"') { if (chunk[i] == '\n') line++; i++ }
                i++
            }
            c == '-' && i + 1 < n && chunk[i + 1] == '-' -> {
                while (i < n && chunk[i] != '\n') i++
            }
            c == '/' && i + 1 < n && chunk[i + 1] == '*' -> {
                i += 2
                while (i + 1 < n && !(chunk[i] == '*' && chunk[i + 1] == '/')) { if (chunk[i] == '\n') line++; i++ }
                i += 2
            }
            c == ';' -> {
                out.add(stmtLine to chunk.substring(stmtStart, i))
                i++
                stmtStart = i
                stmtLine = line
            }
            else -> {
                if (chunk.substring(stmtStart, i).isBlank()) stmtLine = line
                i++
            }
        }
    }
    if (stmtStart < n) out.add(stmtLine to chunk.substring(stmtStart))
    return out.filter { it.second.isNotBlank() }
}

// --- family bucketing ------------------------------------------------------------------------

/** `query-lambda`, `create_table-row_type`, `merge`, ... — statement head + most-salient feature. */
private fun familyOf(statement: Statement): String {
    val head = statement.javaClass.simpleName.toSnake()
    val classes = HashSet<String>()
    fun walk(node: Node) {
        classes.add(node.javaClass.simpleName)
        if (node is Cast && node.isSafe) classes.add("TryCast") // synthetic: TRY_CAST = Cast(safe)
        for (child in node.children) walk(child)
    }
    walk(statement)
    val inherent = INHERENT_FEATURES[head].orEmpty()
    val feature = FEATURE_PRIORITY.firstOrNull { (names, tag) -> tag !in inherent && names.any(classes::contains) }?.second
    return if (feature == null || feature == head) head else "$head-$feature"
}

/**
 * Salience order: exotic constructs first so they never drown in plain SELECT buckets; common
 * plumbing (joins, casts) last; anything unmatched = the bare head family.
 */
private val FEATURE_PRIORITY: List<Pair<Set<String>, String>> = listOf(
    setOf("PatternRecognitionRelation") to "match_recognize",
    setOf("JsonTable") to "json_table",
    setOf("TableFunctionInvocation") to "table_fn",
    setOf("LambdaExpression") to "lambda",
    setOf("JsonQuery", "JsonValue", "JsonExists", "JsonObject", "JsonArray") to "json_fn",
    setOf("TryExpression") to "try",
    setOf("TryCast") to "try_cast",
    // SQL routine bodies (CREATE FUNCTION / WITH FUNCTION) — control-flow syntax PG has no idea of
    setOf("CaseStatement") to "case_stmt",
    setOf("IfStatement") to "if_stmt",
    setOf("LoopStatement", "WhileStatement", "RepeatStatement", "IterateStatement", "LeaveStatement") to "loop",
    setOf("VariableDeclaration") to "declare",
    setOf("CompoundStatement") to "begin_end",
    setOf("FunctionSpecification") to "inline_fn",
    setOf("AtTimeZone", "AtLocal") to "at_time_zone",
    setOf("Trim") to "trim",
    setOf("QueryPeriod") to "time_travel",
    setOf("GroupingSets", "GroupingOperation", "AutoGroupBy") to "grouping",
    setOf("Unnest") to "unnest",
    setOf("Lateral") to "lateral",
    setOf("SampledRelation") to "tablesample",
    setOf("Pivot") to "pivot",
    setOf("QuantifiedComparisonPredicate") to "quantified",
    setOf("IntervalLiteral") to "interval",
    setOf("WindowOperation", "WindowSpecification", "WindowDefinition", "WindowReference") to "window",
    setOf("With") to "cte",
    setOf("Values") to "values",
    setOf("Union", "Intersect", "Except") to "setop",
    setOf("ExistsPredicate") to "exists",
    setOf("SubqueryExpression") to "subquery",
    setOf("RowDataType") to "row_type",
    setOf("Array") to "array",
    setOf("Row") to "row",
    setOf("Extract") to "extract",
    setOf("SimpleCaseExpression", "SearchedCaseExpression") to "case",
    setOf("Cast") to "cast",
    setOf("CurrentTimestamp", "CurrentDate", "CurrentTime", "LocalTime", "LocalTimestamp") to "datetime",
    setOf("Join", "NaturalJoin") to "join",
    setOf("InPredicate") to "in",
    setOf("LikePredicate") to "like",
    setOf("BetweenPredicate") to "between",
)

/** Feature tags a head implies by construction — never used to subdivide that head. */
private val INHERENT_FEATURES: Map<String, Set<String>> = mapOf(
    "create_function" to setOf("inline_fn"),
    "drop_function" to setOf("inline_fn"),
)

private fun String.toSnake(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

private val WS = Regex("\\s+")

private const val PER_FAMILY = 4
private const val OVERSAMPLE = 3 // collect extra pre-sample so dedupe still leaves PER_FAMILY

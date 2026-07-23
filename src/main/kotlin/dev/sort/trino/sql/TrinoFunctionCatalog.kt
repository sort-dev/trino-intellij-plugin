package dev.sort.trino.sql

/**
 * The bundled function/keyword catalog — harvested from a real Trino by
 * `./gradlew harvestFunctionCatalog` (SHOW FUNCTIONS) and from the bundled trino-parser
 * (io.trino.sql.ReservedIdentifiers), refreshed with every trino-jdbc / trino-parser bump.
 *
 * This is Stage 3's "not connected" source. A live per-data-source overlay (the coordinator's own
 * `SHOW FUNCTIONS`, keyed by engine version + session catalog) can layer on top in a later stage
 * and win when present — the DuckDB playbook.
 *
 * Both resources carry `#`-prefixed header comments (engine version, harvest date, counts) which
 * this loader skips. See [FunctionCatalogHarvest][dev.sort.trino.tools] for the row format and the
 * keyword-source rationale (reserved set vs. the fuller lexer vocabulary).
 */
object TrinoFunctionCatalog {

    /** Trino's SHOW FUNCTIONS "Function Type" surface, lowercased -> here. */
    enum class Kind { SCALAR, AGGREGATE, WINDOW, TABLE, OTHER }

    data class Fn(val name: String, val kind: Kind, val returnType: String)

    /** Identifier-named functions only (parity with the harvest filter; Trino ships no operator rows). */
    val functions: List<Fn> by lazy {
        resource("/trino/functions.tsv").lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val cols = line.split('\t')
                if (cols.size < 2) return@mapNotNull null
                val name = cols[0]
                if (!isCompletableName(name)) return@mapNotNull null
                Fn(name, kindOf(cols[1]), cols.getOrElse(2) { "" })
            }
            .toList()
    }

    /** Reserved words (already uppercase in the resource, but normalize defensively). */
    val keywords: Set<String> by lazy {
        resource("/trino/keywords.txt").lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim().uppercase() }
            .toSet()
    }

    /** The operator filter shared with the harvest (same rows, same rule). */
    internal fun isCompletableName(name: String): Boolean =
        name.isNotEmpty() && (name.first().isLetter() || name.first() == '_')

    /** SHOW FUNCTIONS "Function Type" (lowercased) -> [Kind]; single mapping for bundled AND live rows. */
    internal fun kindOf(raw: String): Kind = when (raw.trim().lowercase()) {
        "scalar" -> Kind.SCALAR
        "aggregate" -> Kind.AGGREGATE
        "window" -> Kind.WINDOW
        "table" -> Kind.TABLE
        else -> Kind.OTHER
    }

    private fun resource(path: String): String =
        TrinoFunctionCatalog::class.java.getResourceAsStream(path)?.bufferedReader()?.readText() ?: ""
}

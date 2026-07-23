/*
 * Portions of this file are adapted from StarRocks Support
 * (https://github.com/ycyz97/starrocks-datagrip-plugin), Copyright the StarRocks Support
 * contributors, licensed under the Apache License, Version 2.0, by way of our own
 * doris-intellij-plugin and duckdb-intellij-plugin (github.com/sort-dev). The statement-dispatch
 * and lenient-parsing approach (wordAt / statementContainsAny / lenient consume-to-';') derives
 * from that lineage, modified for Trino syntax. See THIRD_PARTY_NOTICES.md.
 */
package dev.sort.trino.sql

import com.intellij.lang.PsiBuilder
import com.intellij.sql.dialects.postgres.PgParser
import com.intellij.sql.psi.SqlCompositeElementTypes.SQL_STATEMENT

/**
 * Trino statement parsing on the PG foundation, Tier-1 (statement boundaries).
 *
 * When a statement leads with Trino-only syntax the PG grammar cannot represent, consume tokens
 * to the next `;` and wrap them in ONE SQL_STATEMENT node — statement-at-caret / run-block
 * boundaries stay correct everywhere; inner structure arrives with later stages, and REAL errors
 * come from the bundled trino-parser validator (the engine's own grammar). Everything else falls
 * through to super for full PG structure + completion.
 *
 * SEED dispatch set — heads PG has no rule for (census lane refines against the harvested
 * corpus; gates must never steal statements PG parses fine):
 *  - USE catalog[.schema]                        (PG has no USE)
 *  - SHOW ...                                    (Trino's SHOW family: CATALOGS/SCHEMAS/TABLES/
 *                                                 COLUMNS/FUNCTIONS/SESSION/STATS/CREATE/GRANTS/
 *                                                 ROLES — PG's SHOW is a different, tiny thing)
 *  - DESCRIBE / DESC [INPUT|OUTPUT]              (PG lacks DESCRIBE)
 *  - DEALLOCATE PREPARE                          (PG DEALLOCATE differs: no PREPARE keyword)
 *  - SET SESSION / SET PATH / RESET SESSION      (Trino's `SET SESSION cat.prop = v` breaks PG)
 *  - CREATE/DROP CATALOG                         (PG has no catalog DDL)
 */
class TrinoPsiParser : PgParser(false) {

    override fun parseSqlStatement(builder: PsiBuilder, level: Int): Boolean {
        when (wordAt(builder, 0)) {
            "USE", "SHOW", "DESCRIBE", "DESC",
            -> return parseLenientStatement(builder)

            "DEALLOCATE" -> if (wordAt(builder, 1) == "PREPARE") {
                return parseLenientStatement(builder)
            }

            "SET" -> when (wordAt(builder, 1)) {
                "SESSION", "PATH" -> return parseLenientStatement(builder)
            }

            "RESET" -> if (wordAt(builder, 1) == "SESSION") {
                return parseLenientStatement(builder)
            }

            "CREATE", "DROP" -> if (wordAt(builder, 1) == "CATALOG") {
                return parseLenientStatement(builder)
            }
        }
        return super.parseSqlStatement(builder, level)
    }

    // --- lenient machinery (StarRocks -> doris -> duckdb lineage) ---

    private fun parseLenientStatement(builder: PsiBuilder): Boolean {
        val marker = builder.mark()
        while (!builder.eof() && builder.tokenText != ";") builder.advanceLexer()
        marker.done(SQL_STATEMENT)
        return true
    }

    /** The uppercased Nth letter-leading token from the current position, or null. Non-consuming. */
    private fun wordAt(builder: PsiBuilder, offset: Int): String? {
        val marker = builder.mark()
        var current = 0
        var result: String? = null
        while (!builder.eof() && builder.tokenText != ";" && current <= offset) {
            val text = builder.tokenText
            if (text != null && text.firstOrNull()?.isLetter() == true) {
                if (current == offset) {
                    result = text.uppercase()
                    break
                }
                current++
            }
            builder.advanceLexer()
        }
        marker.rollbackTo()
        return result
    }

    /** True if any token before the next ';' matches any of [words] within the window. Non-consuming. */
    @Suppress("unused") // census lane uses this as gates grow
    private fun statementContainsAny(builder: PsiBuilder, vararg words: String): Boolean {
        val expected = words.toHashSet()
        val marker = builder.mark()
        var scanned = 0
        var found = false
        while (!builder.eof() && builder.tokenText != ";" && scanned < MAX_LOOKAHEAD) {
            val text = builder.tokenText
            if (text != null && expected.contains(text.uppercase())) {
                found = true
                break
            }
            builder.advanceLexer()
            scanned++
        }
        marker.rollbackTo()
        return found
    }

    private companion object {
        private const val MAX_LOOKAHEAD = 512
    }
}

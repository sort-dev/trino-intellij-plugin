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
 * Dispatch decisions are bounded look-aheads (≤ [MAX_LOOKAHEAD] tokens, always rolled back), and
 * every gate is census-derived (TrinoCensusScoreboardTest) under the standing rule: gates must
 * NEVER steal statements the PG base parses fine (the doris COPY-exemption lesson) — so heads
 * that PG shares (SET/CREATE/ALTER/EXPLAIN/PREPARE/EXECUTE/ANALYZE/INSERT/WITH/VALUES) only go
 * lenient when a Trino-only marker is visible. NOTE: [TrinoLexer] masks/collapses run FIRST —
 * markers the lexer masked (time travel, `@branch`, routine bodies) are invisible here, which is
 * exactly what lets the masked statements keep their structured PG parse.
 *
 * Always-lenient heads: USE, SHOW, DESCRIBE/DESC (Trino families, PG's are different things),
 * DENY (PG has none), GRANT/REVOKE (Trino's principal/branch/role grammar diverges from PG at
 * several points — privilege sets, TO USER/ROLE grantees, ON BRANCH/SCHEMA targets, catalog
 * roles — a structured PG parse would be wrong-shaped even when it happens to succeed).
 */
class TrinoPsiParser : PgParser(false) {

    override fun parseSqlStatement(builder: PsiBuilder, level: Int): Boolean {
        when (wordAt(builder, 0)) {
            "USE", "SHOW", "DESCRIBE", "DESC", "DENY", "GRANT", "REVOKE",
            -> return parseLenientStatement(builder)

            "DEALLOCATE" -> if (wordAt(builder, 1) == "PREPARE") {
                return parseLenientStatement(builder)
            }

            // SET SESSION cat.prop = v / SET PATH / SET TIME ZONE <expr> all break PG's tiny SET.
            "SET" -> when (wordAt(builder, 1)) {
                "SESSION", "PATH", "TIME" -> return parseLenientStatement(builder)
            }

            "RESET" -> if (wordAt(builder, 1) == "SESSION") {
                return parseLenientStatement(builder)
            }

            // PREPARE name FROM statement — PG's PREPARE uses AS; FROM-first decides.
            "PREPARE" -> if (statementFirstOf(builder, "FROM", "AS") == "FROM") {
                return parseLenientStatement(builder)
            }

            // EXECUTE IMMEDIATE 'sql' / EXECUTE name USING a, b — PG only knows EXECUTE name(args).
            "EXECUTE" -> if (wordAt(builder, 1) == "IMMEDIATE" || statementContainsAny(builder, "USING")) {
                return parseLenientStatement(builder)
            }

            // ANALYZE table WITH (props) — PG's ANALYZE has no WITH clause.
            "ANALYZE" -> if (statementContainsAny(builder, "WITH")) {
                return parseLenientStatement(builder)
            }

            // EXPLAIN (TYPE ...) is Trino-only; EXPLAIN [ANALYZE] of a lenient-head statement
            // must go lenient with it. PG-shaped EXPLAIN (FORMAT/ANALYZE/VERBOSE + query) stays.
            "EXPLAIN" -> {
                val w1 = wordAt(builder, 1)
                if (w1 == "TYPE") return parseLenientStatement(builder)
                // EXPLAIN ANALYZE <query> is PG-valid and stays with super — only EXPLAIN
                // [ANALYZE] of a Trino-lenient head goes lenient with its subject.
                val subject = if (w1 == "ANALYZE") wordAt(builder, 2) else w1
                if (subject in LENIENT_UNDER_EXPLAIN) return parseLenientStatement(builder)
            }

            // WITH FUNCTION f() ... — inline SQL UDFs ahead of the query body (routine bodies are
            // collapsed by TrinoLexer, so consume-to-';' holds the boundary). WITH + bare scalar
            // VALUES rows in a CTE body is equally un-PG.
            "WITH" -> {
                if (wordAt(builder, 1) == "FUNCTION") return parseLenientStatement(builder)
                if (statementHasBareValuesRow(builder)) return parseLenientStatement(builder)
            }

            // VALUES 1, 2 — Trino allows bare scalar rows as a full query; PG demands parens.
            "VALUES" -> if (statementHasBareValuesRow(builder)) {
                return parseLenientStatement(builder)
            }

            // INSERT INTO t VALUES 1, 2 — same bare-scalar-rows divergence.
            "INSERT" -> if (statementHasBareValuesRow(builder)) {
                return parseLenientStatement(builder)
            }

            "CREATE" -> {
                if (wordAt(builder, 1) == "CATALOG" || wordAt(builder, 1) == "BRANCH") {
                    return parseLenientStatement(builder)
                }
                // CREATE OR REPLACE TABLE/MATERIALIZED VIEW — PG has no OR REPLACE for either.
                if (wordAt(builder, 1) == "OR" && wordAt(builder, 2) == "REPLACE" &&
                    wordAt(builder, 3) in setOf("TABLE", "MATERIALIZED")
                ) {
                    return parseLenientStatement(builder)
                }
                // Trino header clauses between the name and AS: COMMENT '...', GRACE PERIOD,
                // WHEN STALE ..., SECURITY DEFINER/INVOKER — none exist in PG's CREATE [MAT.] VIEW.
                if ((wordAt(builder, 1) == "VIEW" || wordAt(builder, 1) == "MATERIALIZED") &&
                    statementContainsAnyBefore(builder, "AS", "COMMENT", "GRACE", "WHEN", "SECURITY")
                ) {
                    return parseLenientStatement(builder)
                }
                // CREATE SCHEMA ... WITH (props) / AUTHORIZATION principal-with-props.
                if (wordAt(builder, 1) == "SCHEMA" && statementContainsAny(builder, "WITH")) {
                    return parseLenientStatement(builder)
                }
                // CREATE TABLE (LIKE t INCLUDING PROPERTIES) — PROPERTIES is not a PG INCLUDING kind.
                if (statementContainsWordThen(builder, "INCLUDING", "PROPERTIES")) {
                    return parseLenientStatement(builder)
                }
                // Parenthesized CTAS body / CTAS whose CTE carries bare scalar VALUES rows.
                if (statementContainsWordThenToken(builder, "AS", "(")) {
                    return parseLenientStatement(builder)
                }
                if (statementContainsWordThen(builder, "AS", "WITH") && statementHasBareValuesRow(builder)) {
                    return parseLenientStatement(builder)
                }
                // SQL/Python routines: LANGUAGE/DETERMINISTIC/SECURITY/CALLED characteristics are
                // Trino-only; a body-less remainder (BEGIN body collapsed by TrinoLexer, so no
                // visible RETURN/AS) means a routine too. CREATE FUNCTION ... RETURN 42 stays
                // with super — the PG grammar parses that form fine.
                if (wordAt(builder, 1) == "FUNCTION" ||
                    (wordAt(builder, 1) == "OR" && wordAt(builder, 2) == "REPLACE" && wordAt(builder, 3) == "FUNCTION")
                ) {
                    if (statementContainsAny(builder, "LANGUAGE", "DETERMINISTIC", "SECURITY", "CALLED") ||
                        !statementContainsAny(builder, "RETURN", "AS")
                    ) {
                        return parseLenientStatement(builder)
                    }
                }
                // CREATE ROLE r WITH ADMIN USER|ROLE p / CREATE ROLE r IN catalog — PG's role
                // options know neither the USER/ROLE grantor forms nor catalogs.
                if (wordAt(builder, 1) == "ROLE") {
                    if (statementContainsWordThen(builder, "ADMIN", "USER") ||
                        statementContainsWordThen(builder, "ADMIN", "ROLE") ||
                        (wordAt(builder, 3) == "IN" && wordAt(builder, 4) !in setOf("ROLE", "GROUP"))
                    ) {
                        return parseLenientStatement(builder)
                    }
                }
            }

            "DROP" -> if (wordAt(builder, 1) == "CATALOG" || wordAt(builder, 1) == "BRANCH") {
                return parseLenientStatement(builder)
            }

            "ALTER" -> {
                if (wordAt(builder, 1) == "BRANCH") return parseLenientStatement(builder)
                // ALTER ... SET AUTHORIZATION / SET PROPERTIES — Trino-only instructions.
                if (statementContainsWordThen(builder, "SET", "AUTHORIZATION") ||
                    statementContainsWordThen(builder, "SET", "PROPERTIES")
                ) {
                    return parseLenientStatement(builder)
                }
                // ALTER TABLE t EXECUTE procedure(...) — table procedures.
                if (wordAt(builder, 1) == "TABLE" && statementContainsAny(builder, "EXECUTE")) {
                    return parseLenientStatement(builder)
                }
                // ALTER VIEW v REFRESH.
                if (wordAt(builder, 1) == "VIEW" && statementContainsAny(builder, "REFRESH")) {
                    return parseLenientStatement(builder)
                }
                // RENAME/DROP COLUMN IF EXISTS — PG puts IF EXISTS on the table, not the column.
                if (statementContainsWordThen(builder, "COLUMN", "IF")) {
                    return parseLenientStatement(builder)
                }
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

    /** True if any of [words] appears BEFORE the first [stop] word (both before ';'). Non-consuming. */
    private fun statementContainsAnyBefore(builder: PsiBuilder, stop: String, vararg words: String): Boolean {
        val expected = words.toHashSet()
        val marker = builder.mark()
        var scanned = 0
        var found = false
        while (!builder.eof() && builder.tokenText != ";" && scanned < MAX_LOOKAHEAD) {
            val text = builder.tokenText?.uppercase()
            if (text == stop) break
            if (text != null && text in expected) {
                found = true
                break
            }
            builder.advanceLexer()
            scanned++
        }
        marker.rollbackTo()
        return found
    }

    /** The FIRST of [words] to appear before the next ';' (uppercased), or null. Non-consuming. */
    private fun statementFirstOf(builder: PsiBuilder, vararg words: String): String? {
        val expected = words.toHashSet()
        val marker = builder.mark()
        var scanned = 0
        var found: String? = null
        while (!builder.eof() && builder.tokenText != ";" && scanned < MAX_LOOKAHEAD) {
            val text = builder.tokenText?.uppercase()
            if (text != null && text in expected) {
                found = text
                break
            }
            builder.advanceLexer()
            scanned++
        }
        marker.rollbackTo()
        return found
    }

    /** True if [word] appears with [next] as the following letter-word, within the window. Non-consuming. */
    private fun statementContainsWordThen(builder: PsiBuilder, word: String, next: String): Boolean {
        val marker = builder.mark()
        var scanned = 0
        var found = false
        var prevWasWord = false
        while (!builder.eof() && builder.tokenText != ";" && scanned < MAX_LOOKAHEAD) {
            val text = builder.tokenText
            if (text != null && text.firstOrNull()?.isLetter() == true) {
                if (prevWasWord && text.equals(next, ignoreCase = true)) { found = true; break }
                prevWasWord = text.equals(word, ignoreCase = true)
            } else if (!text.isNullOrBlank()) {
                prevWasWord = false
            }
            builder.advanceLexer()
            scanned++
        }
        marker.rollbackTo()
        return found
    }

    /** True if [word] appears with the exact token [next] following it. Non-consuming. */
    private fun statementContainsWordThenToken(builder: PsiBuilder, word: String, next: String): Boolean {
        val marker = builder.mark()
        var scanned = 0
        var found = false
        var prevWasWord = false
        while (!builder.eof() && builder.tokenText != ";" && scanned < MAX_LOOKAHEAD) {
            val text = builder.tokenText
            if (!text.isNullOrBlank()) {
                if (prevWasWord && text == next) { found = true; break }
                prevWasWord = text.equals(word, ignoreCase = true)
            }
            builder.advanceLexer()
            scanned++
        }
        marker.rollbackTo()
        return found
    }

    /**
     * True if a visible `VALUES` is followed by anything but `(` — Trino's bare scalar rows,
     * which PG cannot parse in any position. VALUES groups the lexer already collapsed to one
     * ident (FROM/JOIN positions) are invisible here by design. Non-consuming.
     */
    private fun statementHasBareValuesRow(builder: PsiBuilder): Boolean {
        val marker = builder.mark()
        var scanned = 0
        var found = false
        var prevWasValues = false
        while (!builder.eof() && builder.tokenText != ";" && scanned < MAX_LOOKAHEAD) {
            val text = builder.tokenText
            if (!text.isNullOrBlank()) {
                if (prevWasValues && text != "(") { found = true; break }
                prevWasValues = text.equals("VALUES", ignoreCase = true)
            }
            builder.advanceLexer()
            scanned++
        }
        marker.rollbackTo()
        return found
    }

    private companion object {
        private const val MAX_LOOKAHEAD = 512

        /** Trino-only heads that keep EXPLAIN [ANALYZE] out of PG's EXPLAIN grammar. */
        private val LENIENT_UNDER_EXPLAIN =
            setOf("ANALYZE", "SHOW", "DESC", "DESCRIBE", "USE", "DENY", "GRANT", "REVOKE")
    }
}

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
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.sql.dialects.postgres.PgParser
import dev.brikk.house.sql.intellij.parser.containsAny
import dev.brikk.house.sql.intellij.parser.firstOf
import dev.brikk.house.sql.intellij.parser.parseColonParameter
import dev.brikk.house.sql.intellij.parser.parseLenientStatement
import dev.brikk.house.sql.intellij.parser.scanStatement
import dev.brikk.house.sql.intellij.parser.wordAt
import dev.brikk.house.sql.intellij.parser.wordThenToken
import dev.brikk.house.sql.intellij.parser.wordThenWord
import dev.brikk.house.sql.intellij.pipe.PipeStatementGate
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.pipes.TrinoPipeDialect

class TrinoPsiParser : PgParser(false) {
    private val pipeGate = PipeStatementGate(TrinoPipeDialect)

    override fun parseSqlStatement(builder: PsiBuilder, level: Int): Boolean {
        val file = builder.getUserData(FileContextUtil.CONTAINING_FILE_KEY)
        val head = builder.wordAt(0)
        if (pipeGate.parse(builder, BrikkTrinoProfile.forFile(file) != null && head == "FROM")) return true
        val next = builder.wordAt(1)
        // Trino-only heads are lenient; shared heads require a Trino-only clause or bare VALUES.
        // Never steal PG-parseable forms: view markers must precede AS, and routine RETURN stays structured.
        when (head) {
            "USE", "SHOW", "DESCRIBE", "DESC", "DENY", "GRANT", "REVOKE",
            -> return builder.parseLenientStatement()

            "DEALLOCATE" -> if (next == "PREPARE") return builder.parseLenientStatement()
            "SET" -> when (next) {
                "SESSION", "PATH", "TIME" -> return builder.parseLenientStatement()
            }
            "RESET" -> if (next == "SESSION") return builder.parseLenientStatement()
            "PREPARE" -> if (builder.firstOf("FROM", "AS") == "FROM") return builder.parseLenientStatement()
            "EXECUTE" -> if (next == "IMMEDIATE" || builder.containsAny("USING")) {
                return builder.parseLenientStatement()
            }
            "ANALYZE" -> if (builder.containsAny("WITH")) return builder.parseLenientStatement()
            "EXPLAIN" -> {
                if (next == "TYPE") return builder.parseLenientStatement()
                val subject = if (next == "ANALYZE") builder.wordAt(2) else next
                if (subject in LENIENT_UNDER_EXPLAIN) return builder.parseLenientStatement()
            }
            "WITH" -> {
                if (next == "FUNCTION" || builder.hasBareValuesRow()) return builder.parseLenientStatement()
            }
            "VALUES", "INSERT" -> if (builder.hasBareValuesRow()) return builder.parseLenientStatement()
            "CREATE" -> {
                if (next in CATALOG_HEADS) return builder.parseLenientStatement()
                if (next == "OR" && builder.wordAt(2) == "REPLACE" &&
                    builder.wordAt(3) in REPLACE_TABLE_HEADS) return builder.parseLenientStatement()
                if (next in VIEW_HEADS &&
                    builder.containsAnyBefore("AS", "COMMENT", "GRACE", "WHEN", "SECURITY")) {
                    return builder.parseLenientStatement()
                }
                if (next == "SCHEMA" && builder.containsAny("WITH")) return builder.parseLenientStatement()
                if (builder.wordThenWord("INCLUDING", "PROPERTIES")) return builder.parseLenientStatement()
                if (builder.wordThenToken("AS", "(")) return builder.parseLenientStatement()
                if (builder.wordThenWord("AS", "WITH") && builder.hasBareValuesRow()) return builder.parseLenientStatement()
                if (next == "FUNCTION" ||
                    next == "OR" && builder.wordAt(2) == "REPLACE" && builder.wordAt(3) == "FUNCTION") {
                    if (builder.containsAny("LANGUAGE", "DETERMINISTIC", "SECURITY", "CALLED") ||
                        !builder.containsAny("RETURN", "AS")) return builder.parseLenientStatement()
                }
                if (next == "ROLE" &&
                    (builder.wordThenWord("ADMIN", "USER") || builder.wordThenWord("ADMIN", "ROLE") ||
                        builder.wordAt(3) == "IN" && builder.wordAt(4) !in PG_ROLE_MEMBERSHIPS)) {
                    return builder.parseLenientStatement()
                }
            }
            "DROP" -> if (next in CATALOG_HEADS) return builder.parseLenientStatement()
            "ALTER" -> {
                if (next == "BRANCH") return builder.parseLenientStatement()
                if (builder.wordThenWord("SET", "AUTHORIZATION") || builder.wordThenWord("SET", "PROPERTIES")) {
                    return builder.parseLenientStatement()
                }
                if (next == "TABLE" && builder.containsAny("EXECUTE")) return builder.parseLenientStatement()
                if (next == "VIEW" && builder.containsAny("REFRESH")) return builder.parseLenientStatement()
                if (builder.wordThenWord("COLUMN", "IF")) return builder.parseLenientStatement()
            }
        }
        return super.parseSqlStatement(builder, level)
    }

    override fun parseParameterOrVariableReference(builder: PsiBuilder, allowVariable: Boolean): Boolean {
        val file = builder.getUserData(FileContextUtil.CONTAINING_FILE_KEY)
        if (BrikkTrinoProfile.forFile(file) != null && builder.parseColonParameter()) return true
        return super.parseParameterOrVariableReference(builder, allowVariable)
    }

    private fun PsiBuilder.containsAnyBefore(stop: String, vararg words: String): Boolean =
        scanStatement { _, text ->
            when (text.uppercase()) {
                stop -> false
                in words -> true
                else -> null
            }
        } == true

    private fun PsiBuilder.hasBareValuesRow(): Boolean =
        scanStatement { previous, text -> true.takeIf { previous.equals("VALUES", true) && text != "(" } } == true

    private companion object {
        val CATALOG_HEADS = setOf("CATALOG", "BRANCH")
        val REPLACE_TABLE_HEADS = setOf("TABLE", "MATERIALIZED")
        val VIEW_HEADS = setOf("VIEW", "MATERIALIZED")
        val PG_ROLE_MEMBERSHIPS = setOf("ROLE", "GROUP")
        val LENIENT_UNDER_EXPLAIN = setOf("ANALYZE", "SHOW", "DESC", "DESCRIBE", "USE", "DENY", "GRANT", "REVOKE")
    }
}

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
        if (pipeGate.parse(builder, BrikkTrinoProfile.forFile(file) != null && builder.wordAt(0) == "FROM")) return true
        when (builder.wordAt(0)) {
            "USE", "SHOW", "DESCRIBE", "DESC", "DENY", "GRANT", "REVOKE",
            -> return builder.parseLenientStatement()

            "DEALLOCATE" -> if (builder.wordAt(1) == "PREPARE") return builder.parseLenientStatement()
            "SET" -> when (builder.wordAt(1)) {
                "SESSION", "PATH", "TIME" -> return builder.parseLenientStatement()
            }
            "RESET" -> if (builder.wordAt(1) == "SESSION") return builder.parseLenientStatement()
            "PREPARE" -> if (builder.firstOf("FROM", "AS") == "FROM") return builder.parseLenientStatement()
            "EXECUTE" -> if (builder.wordAt(1) == "IMMEDIATE" || builder.containsAny("USING")) {
                return builder.parseLenientStatement()
            }
            "ANALYZE" -> if (builder.containsAny("WITH")) return builder.parseLenientStatement()
            "EXPLAIN" -> {
                val w1 = builder.wordAt(1)
                if (w1 == "TYPE") return builder.parseLenientStatement()
                val subject = if (w1 == "ANALYZE") builder.wordAt(2) else w1
                if (subject in LENIENT_UNDER_EXPLAIN) return builder.parseLenientStatement()
            }
            "WITH" -> {
                if (builder.wordAt(1) == "FUNCTION" || builder.hasBareValuesRow()) return builder.parseLenientStatement()
            }
            "VALUES", "INSERT" -> if (builder.hasBareValuesRow()) return builder.parseLenientStatement()
            "CREATE" -> {
                if (builder.wordAt(1) in setOf("CATALOG", "BRANCH")) return builder.parseLenientStatement()
                if (builder.wordAt(1) == "OR" && builder.wordAt(2) == "REPLACE" &&
                    builder.wordAt(3) in setOf("TABLE", "MATERIALIZED")) return builder.parseLenientStatement()
                if (builder.wordAt(1) in setOf("VIEW", "MATERIALIZED") &&
                    builder.containsAnyBefore("AS", "COMMENT", "GRACE", "WHEN", "SECURITY")) {
                    return builder.parseLenientStatement()
                }
                if (builder.wordAt(1) == "SCHEMA" && builder.containsAny("WITH")) return builder.parseLenientStatement()
                if (builder.wordThenWord("INCLUDING", "PROPERTIES")) return builder.parseLenientStatement()
                if (builder.wordThenToken("AS", "(")) return builder.parseLenientStatement()
                if (builder.wordThenWord("AS", "WITH") && builder.hasBareValuesRow()) return builder.parseLenientStatement()
                if (builder.wordAt(1) == "FUNCTION" ||
                    builder.wordAt(1) == "OR" && builder.wordAt(2) == "REPLACE" && builder.wordAt(3) == "FUNCTION") {
                    if (builder.containsAny("LANGUAGE", "DETERMINISTIC", "SECURITY", "CALLED") ||
                        !builder.containsAny("RETURN", "AS")) return builder.parseLenientStatement()
                }
                if (builder.wordAt(1) == "ROLE" &&
                    (builder.wordThenWord("ADMIN", "USER") || builder.wordThenWord("ADMIN", "ROLE") ||
                        builder.wordAt(3) == "IN" && builder.wordAt(4) !in setOf("ROLE", "GROUP"))) {
                    return builder.parseLenientStatement()
                }
            }
            "DROP" -> if (builder.wordAt(1) in setOf("CATALOG", "BRANCH")) return builder.parseLenientStatement()
            "ALTER" -> {
                if (builder.wordAt(1) == "BRANCH") return builder.parseLenientStatement()
                if (builder.wordThenWord("SET", "AUTHORIZATION") || builder.wordThenWord("SET", "PROPERTIES")) {
                    return builder.parseLenientStatement()
                }
                if (builder.wordAt(1) == "TABLE" && builder.containsAny("EXECUTE")) return builder.parseLenientStatement()
                if (builder.wordAt(1) == "VIEW" && builder.containsAny("REFRESH")) return builder.parseLenientStatement()
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
        val LENIENT_UNDER_EXPLAIN = setOf("ANALYZE", "SHOW", "DESC", "DESCRIBE", "USE", "DENY", "GRANT", "REVOKE")
    }
}

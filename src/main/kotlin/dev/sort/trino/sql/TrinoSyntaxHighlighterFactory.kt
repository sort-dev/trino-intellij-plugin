package dev.sort.trino.sql

import com.intellij.sql.dialects.base.SqlSyntaxHighlighterFactory

/**
 * The platform SQL highlighter for the Trino (Brikk) dialect. Beyond editor coloring, this
 * registration is LOAD-BEARING: the SQL todo/id indexers build their filter lexer from the
 * language's syntax highlighter — without it, ANY file mapped to the dialect crashes the TODO
 * index (PluginException: null from TodoIndex.computeValue; duckdb bisect, 2026-07-21).
 */
class TrinoSyntaxHighlighterFactory : SqlSyntaxHighlighterFactory.Base(TrinoSqlDialect.INSTANCE)

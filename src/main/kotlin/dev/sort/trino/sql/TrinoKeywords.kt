package dev.sort.trino.sql

import dev.brikk.house.sql.metadata.KeywordCategory
import dev.brikk.house.sql.metadata.TRINO_METADATA

internal val TRINO_RESERVED_KEYWORDS: Set<String> =
    TRINO_METADATA.default.keywords?.keywords.orEmpty()
        .filter { it.category == KeywordCategory.RESERVED }
        .mapTo(HashSet()) { it.word.uppercase() }

internal val TRINO_KEYWORDS = setOf(
    "SELECT", "FROM", "WHERE", "GROUP", "ORDER", "BY", "HAVING", "LIMIT", "OFFSET", "FETCH",
    "FIRST", "ROWS", "ONLY", "JOIN", "LEFT", "RIGHT", "FULL", "INNER", "OUTER", "CROSS", "ON",
    "USING", "AS", "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "BETWEEN", "CASE", "WHEN",
    "THEN", "ELSE", "END", "UNION", "EXCEPT", "INTERSECT", "ALL", "ANY", "SOME", "DISTINCT",
    "EXISTS", "CREATE", "TABLE", "REPLACE", "INSERT", "INTO", "VALUES", "UPDATE", "DELETE",
    "SET", "BEGIN", "DECLARE", "IF", "ELSEIF", "WHILE", "REPEAT", "UNTIL", "LOOP", "ITERATE",
    "LEAVE", "RETURN", "RETURNS", "DO", "DEFAULT", "FUNCTION", "LANGUAGE", "CAST", "TRY_CAST",
    "FOR", "TIMESTAMP", "VERSION", "OF", "AT", "LOCAL", "TIME", "ZONE", "INTERVAL", "ROW",
    "MAP", "ARRAY", "MATCH_RECOGNIZE", "PARTITION", "MEASURES", "PATTERN", "DEFINE", "AFTER",
    "MATCH", "SKIP", "PAST", "PIVOT", "OVERFLOW", "ERROR", "TRUNCATE", "WITH", "WITHOUT",
    "COUNT", "COLUMNS", "PATH", "PASSING", "RETURNING", "WRAPPER", "QUOTES", "KEY", "VALUE",
    "EMPTY", "UNKNOWN", "ABSENT", "OVER", "WINDOW", "RECURSIVE", "JSON",
)

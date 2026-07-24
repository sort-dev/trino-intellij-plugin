-- @stmt docs/src/main/sphinx/sql/select.md:1476
SELECT name, x, y
FROM nation
CROSS JOIN LATERAL (SELECT name || ' :-' AS x)
CROSS JOIN LATERAL (SELECT x || ')' AS y);

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6283
SELECT * FROM t, LATERAL (VALUES 1) a(x);

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6297
SELECT * FROM t CROSS JOIN LATERAL (VALUES 1);

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6307
SELECT * FROM t FULL JOIN LATERAL (VALUES 1) ON true;

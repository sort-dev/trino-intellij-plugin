-- @stmt docs/src/main/sphinx/sql/explain-analyze.md:30
EXPLAIN ANALYZE SELECT count(*), clerk FROM orders
WHERE orderdate > date '1995-01-01' GROUP BY clerk;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5147
EXPLAIN ANALYZE ANALYZE foo;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6186
EXPLAIN ANALYZE SELECT * FROM t;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6190
EXPLAIN ANALYZE VERBOSE SELECT * FROM t;

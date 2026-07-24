-- @stmt docs/src/main/sphinx/sql/drop-materialized-view.md:21
DROP MATERIALIZED VIEW orders_by_date;

-- @stmt docs/src/main/sphinx/sql/drop-materialized-view.md:27
DROP MATERIALIZED VIEW IF EXISTS orders_by_date;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:8162
DROP MATERIALIZED VIEW a;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:8167
DROP MATERIALIZED VIEW a.b;

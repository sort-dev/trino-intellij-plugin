-- @stmt docs/src/main/sphinx/sql/drop-view.md:21
DROP VIEW orders_by_date;

-- @stmt docs/src/main/sphinx/sql/drop-view.md:27
DROP VIEW IF EXISTS orders_by_date;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4532
DROP VIEW a;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4537
DROP VIEW a.b;

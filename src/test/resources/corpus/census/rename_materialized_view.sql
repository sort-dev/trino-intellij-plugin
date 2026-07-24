-- @stmt docs/src/main/sphinx/sql/alter-materialized-view.md:39
ALTER MATERIALIZED VIEW people RENAME TO users;

-- @stmt docs/src/main/sphinx/sql/alter-materialized-view.md:46
ALTER MATERIALIZED VIEW IF EXISTS people RENAME TO users;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:8198
ALTER MATERIALIZED VIEW a RENAME TO b;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:8204
ALTER MATERIALIZED VIEW IF EXISTS a RENAME TO b;

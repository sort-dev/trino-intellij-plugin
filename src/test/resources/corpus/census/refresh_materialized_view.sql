-- @stmt docs/src/main/sphinx/sql/refresh-materialized-view.md:6
REFRESH MATERIALIZED VIEW view_name;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:8148
REFRESH MATERIALIZED VIEW test;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:8153
REFRESH MATERIALIZED VIEW "some name that contains space";

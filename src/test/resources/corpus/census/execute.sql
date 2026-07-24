-- @stmt docs/src/main/sphinx/functions/table.md:214
EXECUTE stmt USING 100, 1;

-- @stmt docs/src/main/sphinx/sql/execute.md:24
EXECUTE my_select1;

-- @stmt docs/src/main/sphinx/sql/execute.md:35
EXECUTE my_select2 USING 1, 3;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6922
EXECUTE myquery;

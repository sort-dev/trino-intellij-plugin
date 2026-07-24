-- @stmt docs/src/main/sphinx/sql/alter-view.md:20
ALTER VIEW people RENAME TO users;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5021
ALTER VIEW a RENAME TO b;

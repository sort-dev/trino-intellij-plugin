-- @stmt docs/src/main/sphinx/sql/alter-table.md:80
ALTER TABLE users RENAME TO people;

-- @stmt docs/src/main/sphinx/sql/alter-table.md:86
ALTER TABLE IF EXISTS users RENAME TO people;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4801
ALTER TABLE a RENAME TO b;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4807
ALTER TABLE IF EXISTS a RENAME TO b;

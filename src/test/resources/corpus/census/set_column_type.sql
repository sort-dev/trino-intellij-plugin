-- @stmt docs/src/main/sphinx/sql/alter-table.md:149
ALTER TABLE users ALTER COLUMN id SET DATA TYPE bigint;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5490
ALTER TABLE foo.t ALTER COLUMN a SET DATA TYPE bigint;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5500
ALTER TABLE IF EXISTS foo.t ALTER COLUMN b SET DATA TYPE double;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestStatementBuilder.java:213
alter table foo alter column x set data type bigint;

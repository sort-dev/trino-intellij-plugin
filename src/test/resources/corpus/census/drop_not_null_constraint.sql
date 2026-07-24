-- @stmt docs/src/main/sphinx/sql/alter-table.md:155
ALTER TABLE users ALTER COLUMN id DROP NOT NULL;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5514
ALTER TABLE foo.t ALTER COLUMN a DROP NOT NULL;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5523
ALTER TABLE IF EXISTS foo.t ALTER COLUMN a DROP NOT NULL;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestStatementBuilder.java:216
alter table foo alter column x drop not null;

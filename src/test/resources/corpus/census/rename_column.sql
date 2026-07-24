-- @stmt docs/src/main/sphinx/sql/alter-table.md:136
ALTER TABLE users RENAME COLUMN id TO user_id;

-- @stmt docs/src/main/sphinx/sql/alter-table.md:143
ALTER TABLE IF EXISTS users RENAME column IF EXISTS id to user_id;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4919
ALTER TABLE foo.t RENAME COLUMN a TO b;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4930
ALTER TABLE IF EXISTS foo.t RENAME COLUMN a TO b;

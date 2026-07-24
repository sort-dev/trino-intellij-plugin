-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5444
ALTER TABLE foo.t ALTER COLUMN a SET DEFAULT 123;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5454
ALTER TABLE IF EXISTS foo.t ALTER COLUMN b SET DEFAULT 123;

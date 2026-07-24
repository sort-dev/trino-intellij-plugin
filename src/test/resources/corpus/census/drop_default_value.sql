-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5468
ALTER TABLE foo.t ALTER COLUMN a DROP DEFAULT;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5477
ALTER TABLE IF EXISTS foo.t ALTER COLUMN b DROP DEFAULT;

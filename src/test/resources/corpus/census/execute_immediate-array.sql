-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6953
EXECUTE IMMEDIATE 'SELECT ?, ? FROM foo' USING 1, 'abc', ARRAY ['hello'];

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7169
SHOW STATS FOR (
   WITH t AS (SELECT 1 )
   SELECT * FROM t);

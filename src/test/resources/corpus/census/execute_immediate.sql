-- @stmt docs/src/main/sphinx/sql/execute-immediate.md:19
EXECUTE IMMEDIATE
'SELECT name FROM nation';

-- @stmt docs/src/main/sphinx/sql/execute-immediate.md:26
EXECUTE IMMEDIATE
'SELECT name FROM nation WHERE regionkey = ? and nationkey < ?'
USING 1, 3;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:6942
EXECUTE IMMEDIATE 'SELECT * FROM foo';

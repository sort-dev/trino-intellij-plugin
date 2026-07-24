-- @stmt docs/src/main/sphinx/sql/show-session.md:18
SHOW SESSION LIKE 'query%';

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2106
SHOW SESSION;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2107
SHOW SESSION LIKE '%';

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2108
SHOW SESSION LIKE '%' ESCAPE '$';

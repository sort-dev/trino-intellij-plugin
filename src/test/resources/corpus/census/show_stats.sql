-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7046
SHOW STATS FOR t;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7049
SHOW STATS FOR s.t;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7053
SHOW STATS FOR c.s.t;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7105
SHOW STATS FOR (SELECT * FROM t LIMIT 10);

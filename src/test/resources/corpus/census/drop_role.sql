-- @stmt docs/src/main/sphinx/sql/drop-role.md:28
DROP ROLE admin;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7624
DROP ROLE role;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7626
DROP ROLE IF EXISTS role;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7628
DROP ROLE "role";

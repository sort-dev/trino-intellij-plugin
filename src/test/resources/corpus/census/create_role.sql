-- @stmt docs/src/main/sphinx/sql/create-role.md:28
CREATE ROLE admin;

-- @stmt docs/src/main/sphinx/sql/create-role.md:34
CREATE ROLE moderator WITH ADMIN USER bob;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7543
CREATE ROLE role;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7549
CREATE ROLE role1 WITH ADMIN admin;

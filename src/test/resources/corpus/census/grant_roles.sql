-- @stmt docs/src/main/sphinx/sql/grant-roles.md:35
GRANT bar TO USER foo;

-- @stmt docs/src/main/sphinx/sql/grant-roles.md:41
GRANT bar, foo TO USER baz, ROLE qux WITH ADMIN OPTION;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7641
GRANT role1 TO user1;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7649
GRANT role1, role2, role3 TO user1, USER user2, ROLE role4 WITH ADMIN OPTION;

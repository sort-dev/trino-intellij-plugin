-- @stmt docs/src/main/sphinx/sql/revoke-roles.md:36
REVOKE bar FROM USER foo;

-- @stmt docs/src/main/sphinx/sql/revoke-roles.md:42
REVOKE ADMIN OPTION FOR bar, foo FROM USER baz, ROLE qux;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7726
REVOKE role1 FROM user1;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7734
REVOKE ADMIN OPTION FOR role1, role2, role3 FROM user1, USER user2, ROLE role4;

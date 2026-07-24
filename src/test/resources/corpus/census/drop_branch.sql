-- @stmt docs/src/main/sphinx/sql/drop-branch.md:22
DROP BRANCH audit IN TABLE orders;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2513
DROP BRANCH b IN TABLE t;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2520
DROP BRANCH IF EXISTS b IN TABLE t;

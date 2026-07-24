-- @stmt docs/src/main/sphinx/sql/create-branch.md:35
CREATE BRANCH audit IN TABLE orders;

-- @stmt docs/src/main/sphinx/sql/create-branch.md:41
CREATE BRANCH audit IN TABLE orders FROM dev;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2434
CREATE BRANCH b IN TABLE t;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2443
CREATE BRANCH b IN TABLE t FROM other;

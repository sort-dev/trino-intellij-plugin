-- @stmt docs/src/main/sphinx/sql/alter-branch.md:6
ALTER BRANCH source_branch IN TABLE table_name FAST FORWARD TO target_branch;

-- @stmt docs/src/main/sphinx/sql/alter-branch.md:20
ALTER BRANCH main IN TABLE orders FAST FORWARD TO audit;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2531
ALTER BRANCH from_branch IN TABLE t FAST FORWARD TO to_branch;

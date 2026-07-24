-- @stmt docs/src/main/sphinx/sql/reset-session.md:18
RESET SESSION query_max_run_time;

-- @stmt docs/src/main/sphinx/sql/reset-session.md:19
RESET SESSION hive.optimized_reader_enabled;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2074
RESET SESSION foo.bar;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2078
RESET SESSION foo;

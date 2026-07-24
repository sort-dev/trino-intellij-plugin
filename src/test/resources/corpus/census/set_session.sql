-- @stmt docs/src/main/sphinx/appendix/from-hive.md:181
SET SESSION hdfs.insert_existing_partitions_behavior = 'OVERWRITE';

-- @stmt docs/src/main/sphinx/sql/set-session.md:43
SET SESSION query_max_run_time = '10m';

-- @stmt docs/src/main/sphinx/sql/set-session.md:50
SET SESSION example.incremental_refresh_enabled=false;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2046
SET SESSION foo = 'bar';

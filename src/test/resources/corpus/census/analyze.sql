-- @stmt docs/src/main/sphinx/connector/delta-lake.md:1154
ANALYZE table_schema.table_name;

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:1174
ANALYZE example_table WITH(files_modified_after = TIMESTAMP '2021-08-23
16:43:01.321 Z');

-- @stmt docs/src/main/sphinx/connector/hive.md:686
ANALYZE example.web.request_logs;

-- @stmt docs/src/main/sphinx/connector/iceberg.md:2334
ANALYZE table_name;

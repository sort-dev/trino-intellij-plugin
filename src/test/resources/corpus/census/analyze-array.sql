-- @stmt docs/src/main/sphinx/connector/delta-lake.md:1185
ANALYZE example_table WITH(columns = ARRAY['nationkey', 'regionkey']);

-- @stmt docs/src/main/sphinx/connector/hive.md:1405
ANALYZE table_name WITH (
    partitions = ARRAY[
        ARRAY['p1_value1', 'p1_value2'],
        ARRAY['p2_value1', 'p2_value2']]);

-- @stmt docs/src/main/sphinx/connector/hive.md:1420
ANALYZE table_name WITH (
    partitions = ARRAY[ARRAY['p2_value1', 'p2_value2']],
    columns = ARRAY['col_1', 'col_2']);

-- @stmt docs/src/main/sphinx/connector/iceberg.md:2345
ANALYZE table_name WITH (columns = ARRAY['col_1', 'col_2']);

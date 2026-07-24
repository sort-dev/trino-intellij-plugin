-- @stmt docs/src/main/sphinx/connector/hive.md:710
CALL system.create_empty_partition(
    schema_name => 'web',
    table_name => 'page_views',
    partition_columns => ARRAY['ds', 'country'],
    partition_values => ARRAY['2016-08-09', 'US']);

-- @stmt docs/src/main/sphinx/connector/hive.md:720
CALL system.drop_stats(
    schema_name => 'web',
    table_name => 'page_views',
    partition_values => ARRAY[ARRAY['2016-08-09', 'US']]);

-- @stmt docs/src/main/sphinx/connector/hive.md:1438
CALL system.drop_stats(
    schema_name => 'schema',
    table_name => 'table',
    partition_values => ARRAY[ARRAY['p2_value1', 'p2_value2']]);

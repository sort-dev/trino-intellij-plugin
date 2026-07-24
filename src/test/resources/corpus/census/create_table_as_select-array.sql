-- @stmt docs/src/main/sphinx/connector/delta-lake.md:720
CREATE OR REPLACE TABLE example_table
WITH (partitioned_by = ARRAY['a'])
AS SELECT * FROM another_table;

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:796
CREATE TABLE example.default.example_partitioned_table
WITH (
  location = 's3://my-bucket/a/path',
  partitioned_by = ARRAY['regionkey'],
  checkpoint_interval = 5,
  change_data_feed_enabled = false,
  column_mapping_mode = 'name',
  deletion_vectors_enabled = false
)
AS SELECT name, comment, regionkey FROM tpch.tiny.nation;

-- @stmt docs/src/main/sphinx/connector/iceberg.md:2006
CREATE OR REPLACE TABLE example_table
WITH (sorted_by = ARRAY['a'])
AS SELECT * FROM another_table;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:3939
CREATE TABLE foo
WITH ( string = 'bar', long = 42, computed = 'ban' || 'ana', a  = ARRAY[ 'v1', 'v2' ] )
AS
SELECT * FROM t;

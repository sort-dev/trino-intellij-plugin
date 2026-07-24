-- @stmt docs/src/main/sphinx/connector/clickhouse.md:136
CREATE TABLE default.trino_ck (
  id int NOT NULL,
  birthday DATE NOT NULL,
  name VARCHAR,
  age BIGINT,
  logdate DATE NOT NULL
)
WITH (
  engine = 'MergeTree',
  order_by = ARRAY['id', 'birthday'],
  partition_by = ARRAY['toYYYYMM(logdate)'],
  primary_key = ARRAY['id'],
  sample_by = 'id'
);

-- @stmt docs/src/main/sphinx/connector/hive.md:618
CREATE TABLE example.web.page_views (
  view_time TIMESTAMP,
  user_id BIGINT,
  page_url VARCHAR,
  ds DATE,
  country VARCHAR
)
WITH (
  format = 'ORC',
  partitioned_by = ARRAY['ds', 'country'],
  bucketed_by = ARRAY['user_id'],
  bucket_count = 50
);

-- @stmt docs/src/main/sphinx/connector/iceberg.md:540
CREATE TABLE example_table (
    c1 INTEGER,
    c2 DATE,
    c3 DOUBLE
)
WITH (
    format = 'PARQUET',
    partitioning = ARRAY['c1', 'c2'],
    sorted_by = ARRAY['c3'],
    location = 's3://my-bucket/a/path/'
);

-- @stmt docs/src/main/sphinx/connector/iceberg.md:1178
CREATE TABLE test_table (
    c1 INTEGER,
    c2 DATE,
    c3 DOUBLE)
WITH (
    format = 'PARQUET',
    partitioning = ARRAY['c1', 'c2'],
    location = '/var/example_tables/test_table');

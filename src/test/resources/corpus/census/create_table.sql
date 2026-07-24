-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table.sql:17
CREATE TABLE like_csv_table (LIKE csv_table INCLUDING PROPERTIES);

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table_with_custom_parameters.sql:17
CREATE TABLE like_csv_table (LIKE csv_table_with_custom_parameters INCLUDING PROPERTIES);

-- @stmt docs/src/main/sphinx/connector/blackhole.md:53
CREATE TABLE example.test.nation (
  nationkey BIGINT,
  name VARCHAR
)
WITH (
  split_count = 500,
  pages_per_split = 1000,
  rows_per_page = 2000
);

-- @stmt docs/src/main/sphinx/connector/blackhole.md:76
CREATE TABLE example.test.nation (
  nationkey BIGINT,
  name VARCHAR
)
WITH (
  split_count = 500,
  pages_per_split = 1000,
  rows_per_page = 2000,
  field_length = 100
);

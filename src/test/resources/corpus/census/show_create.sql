-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table.sql:12
SHOW CREATE TABLE csv_table;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table.sql:18
SHOW CREATE TABLE like_csv_table;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table_with_custom_parameters.sql:12
SHOW CREATE TABLE csv_table_with_custom_parameters;

-- @stmt docs/src/main/sphinx/connector/faker.md:312
SHOW CREATE TABLE production.public.customers;

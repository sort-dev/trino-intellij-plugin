-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/catalog/describe.sql:1
-- database: trino; groups: base_sql; queryType: SELECT; tables: nation
describe nation;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/catalog/showColumns.sql:1
-- database: trino; groups: base_sql; queryType: SELECT
show columns from system.runtime.nodes;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table.sql:7
DESCRIBE csv_table;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table_with_custom_parameters.sql:7
DESCRIBE csv_table_with_custom_parameters;

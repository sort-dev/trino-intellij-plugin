-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/catalog/showSchemas.sql:1
-- database: trino; groups: base_sql; queryType: SELECT
show schemas;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/system/showSystemSchemas.sql:1
-- database: trino; groups: system; queryType: SELECT
SHOW SCHEMAS FROM system;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpch_connector/showSchemas.sql:1
-- database: trino; groups: tpch_connector; queryType: SELECT
SHOW SCHEMAS FROM tpch;

-- @stmt docs/src/main/sphinx/connector/clickhouse.md:102
SHOW SCHEMAS FROM example;

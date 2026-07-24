-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/catalog/showTables.sql:1
-- database: trino; groups: jmx,base_sql; queryType: SELECT
SHOW TABLES FROM jmx.current;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/system/showTablesSystemInformationSchema.sql:1
-- database: trino; groups: system; queryType: SELECT
SHOW TABLES FROM system.information_schema;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/system/showTablesSystemMetadata.sql:1
-- database: trino; groups: system; queryType: SELECT
SHOW TABLES FROM system.metadata;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/system/showTablesSystemRuntime.sql:1
-- database: trino; groups: system; queryType: SELECT
SHOW TABLES FROM system.runtime;

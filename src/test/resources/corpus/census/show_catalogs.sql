-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/catalog/showCatalogs.sql:1
-- database: trino; groups: base_sql; queryType: SELECT
show catalogs;

-- @stmt docs/src/main/sphinx/sql/show-catalogs.md:18
SHOW CATALOGS LIKE 't%';

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2114
SHOW CATALOGS;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:2115
SHOW CATALOGS LIKE '%';

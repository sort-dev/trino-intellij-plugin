-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/convertion_functions/tryCastAccessible.sql:1
-- database: trino; groups: qe, conversion_functions
SELECT TRY_CAST(10 as VARCHAR), TRY_CAST('ala' as BIGINT);

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestStatementBuilder.java:130
select cast('123' as bigint), try_cast('foo' as bigint);

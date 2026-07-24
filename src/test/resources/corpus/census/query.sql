-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/aggregate/aggregateOverFunction.sql:1
-- database: trino; groups: aggregate; tables: datatype
select max(upper(c_string)), min(upper(c_string)) from datatype;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/aggregate/average.sql:1
-- database: trino; groups: aggregate; tables: datatype
select avg(c_bigint), avg(c_double) from datatype;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/color_functions/bar.sql:1
-- database: trino; groups: color;
SELECT bar(1, 10), bar(1, 10, rgb(255, 0, 0), color('#0f0'));

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/csv_table.sql:3
SELECT * FROM csv_table;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/string_functions/likeOperatorWorks.sql:1
-- database: trino; groups: qe, string_functions
select name from tpch.tiny.nation where name like '%AN';

-- @stmt docs/src/main/sphinx/functions/comparison.md:292
SELECT 'South_America' LIKE 'South\_America' ESCAPE '\';

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/map_functions/mapRetrieveOperatorWorks.sql:1
-- database: trino; groups: qe, map_functions
select MAP(ARRAY ['ala', 'kot'], ARRAY[3, 4]) ['kot'];

-- @stmt docs/src/main/sphinx/appendix/from-hive.md:37
SELECT ARRAY[1, 2, 3] AS my_array;

-- @stmt docs/src/main/sphinx/functions/ai.md:187
SELECT ai_classify('Buy now!', ARRAY['spam', 'not spam']);

-- @stmt docs/src/main/sphinx/functions/ai.md:196
SELECT ai_extract('John is 25 years old', ARRAY['name', 'age']);

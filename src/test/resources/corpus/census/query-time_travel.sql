-- @stmt docs/src/main/sphinx/connector/delta-lake.md:471
SELECT *
FROM example.testdb.customer_orders FOR VERSION AS OF 3;

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:481
SELECT *
FROM example.testdb.customer_orders FOR TIMESTAMP AS OF TIMESTAMP '2022-03-23 09:59:29.803 America/Los_Angeles';

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:497
SELECT *
FROM example.testdb.customer_orders FOR TIMESTAMP AS OF DATE '2022-03-23';

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:502
SELECT *
FROM example.testdb.customer_orders FOR TIMESTAMP AS OF TIMESTAMP '2022-03-23 00:00:00';

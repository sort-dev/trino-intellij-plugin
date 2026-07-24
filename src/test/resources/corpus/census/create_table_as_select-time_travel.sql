-- @stmt docs/src/main/sphinx/connector/delta-lake.md:488
CREATE OR REPLACE TABLE example.testdb.customer_orders AS
SELECT *
FROM example.testdb.customer_orders FOR TIMESTAMP AS OF TIMESTAMP '2022-03-23 09:59:29.803 America/Los_Angeles';

-- @stmt docs/src/main/sphinx/connector/iceberg.md:2039
CREATE OR REPLACE TABLE example.testdb.customer_orders AS
SELECT *
FROM example.testdb.customer_orders FOR TIMESTAMP AS OF TIMESTAMP '2022-03-23 09:59:29.803 Europe/Vienna';

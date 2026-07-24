-- @stmt docs/src/main/sphinx/connector/blackhole.md:31
CREATE TABLE example.test.nation AS
SELECT * from tpch.tiny.nation;

-- @stmt docs/src/main/sphinx/connector/faker.md:373
CREATE TABLE generator.default.customer AS TABLE production.public.customer;

-- @stmt docs/src/main/sphinx/connector/faker.md:397
CREATE TABLE generator.default.orders AS TABLE tpch.tiny.orders;

-- @stmt docs/src/main/sphinx/connector/iceberg.md:561
CREATE TABLE tiny_nation
WITH (
    format = 'PARQUET'
)
AS
    SELECT *
    FROM nation
    WHERE nationkey < 10;

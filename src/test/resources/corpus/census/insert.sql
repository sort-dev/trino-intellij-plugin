-- @stmt docs/src/main/sphinx/connector/blackhole.md:38
INSERT INTO example.test.nation
SELECT * FROM tpch.tiny.nation;

-- @stmt docs/src/main/sphinx/connector/faker.md:329
INSERT INTO production.public.customers
SELECT *
FROM generator.default.customers
LIMIT 100;

-- @stmt docs/src/main/sphinx/connector/memory.md:31
INSERT INTO example.default.nation
SELECT * FROM tpch.tiny.nation;

-- @stmt docs/src/main/sphinx/sql/insert.md:24
INSERT INTO orders
SELECT * FROM new_orders;

-- @stmt docs/src/main/sphinx/connector/faker.md:360
CREATE TABLE generator.default.customer AS
SELECT *
FROM production.public.customer
WHERE created_at > CURRENT_DATE - INTERVAL '1' YEAR;

-- @stmt docs/src/main/sphinx/functions/conditional.md:219
SELECT TRY(CAST(origin_zip AS BIGINT)) FROM shipping;

-- @stmt docs/src/main/sphinx/functions/conditional.md:245
SELECT COALESCE(TRY(total_cost / packages), 0) AS per_package FROM shipping;

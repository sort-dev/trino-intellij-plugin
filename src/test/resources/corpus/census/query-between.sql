-- @stmt docs/src/main/sphinx/functions/comparison.md:35
SELECT 3 BETWEEN 2 AND 6;

-- @stmt docs/src/main/sphinx/functions/comparison.md:47
SELECT 3 NOT BETWEEN 2 AND 6;

-- @stmt docs/src/main/sphinx/functions/comparison.md:60
SELECT NULL BETWEEN 2 AND 4;

-- @stmt docs/src/main/sphinx/functions/comparison.md:60
-- null

SELECT 2 BETWEEN NULL AND 6;

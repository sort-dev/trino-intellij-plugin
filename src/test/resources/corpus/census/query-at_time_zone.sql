-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/array_functions/arrayCreationAccessible.sql:1
-- database: trino; groups: qe, horology_functions
SELECT timezone_hour(TIMESTAMP '2001-08-22 03:04:05.321' at time zone 'Asia/Oral'),
       timezone_minute(TIMESTAMP '2001-08-22 03:04:05.321' at time zone 'Asia/Oral');

-- @stmt docs/src/main/sphinx/functions/datetime.md:28
-- 2012-10-31 01:00:00.000 UTC

SELECT timestamp '2012-10-31 01:00 UTC' AT TIME ZONE 'America/Los_Angeles';

-- @stmt docs/src/main/sphinx/functions/datetime.md:38
SELECT timestamp '2012-10-31 01:00 UTC' AT LOCAL;

-- @stmt docs/src/main/sphinx/release/release-0.66.md:100
SELECT TIMESTAMP '2014-03-14 09:30:00 Europe/Berlin'
     AT TIME ZONE 'America/Los_Angeles';

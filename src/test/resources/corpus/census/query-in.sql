-- @stmt docs/src/main/sphinx/connector/pinot.md:106
SELECT foo
FROM pinot_table
WHERE bar = 3 AND baz IN ('ONE', 'TWO', 'THREE')
LIMIT 25000;

-- @stmt docs/src/main/sphinx/connector/pinot.md:120
SELECT *
FROM example.default."SELECT MAX(col1), COUNT(col2) FROM pinot_table GROUP BY col3, col4"
WHERE col3 IN ('FOO', 'BAR') AND col4 > 50
LIMIT 30000;

-- @stmt docs/src/main/sphinx/connector/redis.md:354
SELECT * FROM nation WHERE redis_key IN ('CANADA', 'POLAND');

-- @stmt docs/src/main/sphinx/functions/comparison.md:318
SELECT * FROM region WHERE name IN ('AMERICA', 'EUROPE');

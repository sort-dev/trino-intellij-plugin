-- @stmt docs/src/main/sphinx/sql/explain-analyze.md:88
EXPLAIN ANALYZE VERBOSE SELECT count(clerk) OVER() FROM orders
WHERE orderdate > date '1995-01-01';

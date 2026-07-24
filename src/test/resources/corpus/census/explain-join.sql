-- @stmt docs/src/main/sphinx/admin/dynamic-filtering.md:72
EXPLAIN
SELECT count(*)
FROM store_sales
JOIN date_dim ON store_sales.ss_sold_date_sk = date_dim.d_date_sk
WHERE d_following_holiday='Y' AND d_year = 2000;

-- @stmt docs/src/main/sphinx/optimizer/pushdown.md:198
EXPLAIN SELECT c.custkey, o.orderkey
FROM orders o JOIN customer c ON c.custkey = o.custkey;

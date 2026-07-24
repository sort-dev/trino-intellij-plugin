-- @stmt docs/src/main/sphinx/connector/hive.md:650
DELETE FROM example.web.page_views
WHERE ds = DATE '2016-08-09'
  AND country = 'US';

-- @stmt docs/src/main/sphinx/connector/iceberg.md:833
DELETE FROM example.testdb.customer_orders
WHERE country = 'US';

-- @stmt docs/src/main/sphinx/release/release-0.123.md:48
DELETE FROM orders
WHERE order_date = '2015-10-15' AND order_region = 'APAC';

-- @stmt docs/src/main/sphinx/sql/delete.md:19
DELETE FROM lineitem WHERE shipmode = 'AIR';

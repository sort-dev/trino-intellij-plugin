-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q15.sql:1
-- database: trino; groups: tpch; tables: lineitem,supplier
CREATE OR REPLACE VIEW revenue AS
  SELECT
    l_suppkey AS supplier_no,
    sum(l_extendedprice * (1 - l_discount)) AS total_revenue
  FROM
    lineitem
  WHERE
    l_shipdate >= DATE '1996-01-01'
    AND l_shipdate < DATE '1996-01-01' + INTERVAL '3' MONTH
GROUP BY
  l_suppkey;

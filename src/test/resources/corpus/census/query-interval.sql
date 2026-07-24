-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q01.sql:1
-- database: trino; groups: tpch; tables: lineitem
SELECT
  l_returnflag,
  l_linestatus,
  sum(l_quantity)                                       AS sum_qty,
  sum(l_extendedprice)                                  AS sum_base_price,
  sum(l_extendedprice * (1 - l_discount))               AS sum_disc_price,
  sum(l_extendedprice * (1 - l_discount) * (1 + l_tax)) AS sum_charge,
  avg(l_quantity)                                       AS avg_qty,
  avg(l_extendedprice)                                  AS avg_price,
  avg(l_discount)                                       AS avg_disc,
  count(*)                                              AS count_order
FROM
  lineitem
WHERE
  l_shipdate <= DATE '1998-12-01' - INTERVAL '90' DAY
GROUP BY
l_returnflag,
l_linestatus
ORDER BY
l_returnflag,
l_linestatus;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q04.sql:1
-- database: trino; groups: tpch; tables: orders,lineitem
SELECT
  o_orderpriority,
  count(*) AS order_count
FROM orders
WHERE
  o_orderdate >= DATE '1993-07-01'
  AND o_orderdate < DATE '1993-07-01' + INTERVAL '3' MONTH
AND EXISTS (
SELECT *
FROM lineitem
WHERE
l_orderkey = o_orderkey
AND l_commitdate < l_receiptdate
)
GROUP BY
o_orderpriority
ORDER BY
o_orderpriority;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q05.sql:1
-- database: trino; groups: tpch; tables: customer,orders,lineitem,supplier,nation,region
SELECT
  n_name,
  sum(l_extendedprice * (1 - l_discount)) AS revenue
FROM
  customer,
  orders,
  lineitem,
  supplier,
  nation,
  region
WHERE
  c_custkey = o_custkey
  AND l_orderkey = o_orderkey
  AND l_suppkey = s_suppkey
  AND c_nationkey = s_nationkey
  AND s_nationkey = n_nationkey
  AND n_regionkey = r_regionkey
  AND r_name = 'ASIA'
  AND o_orderdate >= DATE '1994-01-01'
  AND o_orderdate < DATE '1994-01-01' + INTERVAL '1' YEAR
GROUP BY
n_name
ORDER BY
revenue DESC;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q06.sql:1
-- database: trino; groups: tpch; tables: lineitem
SELECT sum(l_extendedprice * l_discount) AS revenue
FROM
  lineitem
WHERE
  l_shipdate >= DATE '1994-01-01'
  AND l_shipdate < DATE '1994-01-01' + INTERVAL '1' YEAR
AND l_discount BETWEEN decimal '0.06' - decimal '0.01' AND decimal '0.06' + decimal '0.01'
AND l_quantity < 24;

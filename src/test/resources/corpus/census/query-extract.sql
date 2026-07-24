-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q07.sql:1
-- database: trino; groups: tpch; tables: supplier,lineitem,orders,customer,nation
SELECT
  supp_nation,
  cust_nation,
  l_year,
  sum(volume) AS revenue
FROM (
       SELECT
         n1.n_name                          AS supp_nation,
         n2.n_name                          AS cust_nation,
         extract(YEAR FROM l_shipdate)      AS l_year,
         l_extendedprice * (1 - l_discount) AS volume
       FROM
         supplier,
         lineitem,
         orders,
         customer,
         nation n1,
         nation n2
       WHERE
         s_suppkey = l_suppkey
         AND o_orderkey = l_orderkey
         AND c_custkey = o_custkey
         AND s_nationkey = n1.n_nationkey
         AND c_nationkey = n2.n_nationkey
         AND (
           (n1.n_name = 'FRANCE' AND n2.n_name = 'GERMANY')
           OR (n1.n_name = 'GERMANY' AND n2.n_name = 'FRANCE')
         )
         AND l_shipdate BETWEEN DATE '1995-01-01' AND DATE '1996-12-31'
     ) AS shipping
GROUP BY
  supp_nation,
  cust_nation,
  l_year
ORDER BY
  supp_nation,
  cust_nation,
  l_year;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q08.sql:1
-- database: trino; groups: tpch; tables: part,supplier,lineitem,orders,customer,nation,region
SELECT
  o_year,
  sum(CASE
      WHEN nation = 'BRAZIL'
        THEN volume
      ELSE 0
      END) / sum(volume) AS mkt_share
FROM (
       SELECT
         extract(YEAR FROM o_orderdate)     AS o_year,
         l_extendedprice * (1 - l_discount) AS volume,
         n2.n_name                          AS nation
       FROM
         part,
         supplier,
         lineitem,
         orders,
         customer,
         nation n1,
         nation n2,
         region
       WHERE
         p_partkey = l_partkey
         AND s_suppkey = l_suppkey
         AND l_orderkey = o_orderkey
         AND o_custkey = c_custkey
         AND c_nationkey = n1.n_nationkey
         AND n1.n_regionkey = r_regionkey
         AND r_name = 'AMERICA'
         AND s_nationkey = n2.n_nationkey
         AND o_orderdate BETWEEN DATE '1995-01-01' AND DATE '1996-12-31'
         AND p_type = 'ECONOMY ANODIZED STEEL'
     ) AS all_nations
GROUP BY
  o_year
ORDER BY
  o_year;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q09.sql:1
-- database: trino; groups: tpch, large_query; tables: part,supplier,lineitem,partsupp,orders,nation
SELECT
  nation,
  o_year,
  sum(amount) AS sum_profit
FROM (
       SELECT
         n_name                                                          AS nation,
         extract(YEAR FROM o_orderdate)                                  AS o_year,
         l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity AS amount
       FROM
         part,
         supplier,
         lineitem,
         partsupp,
         orders,
         nation
       WHERE
         s_suppkey = l_suppkey
         AND ps_suppkey = l_suppkey
         AND ps_partkey = l_partkey
         AND p_partkey = l_partkey
         AND o_orderkey = l_orderkey
         AND s_nationkey = n_nationkey
         AND p_name LIKE '%green%'
     ) AS profit
GROUP BY
  nation,
  o_year
ORDER BY
  nation,
  o_year DESC;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/horology_functions/extractAccessible.sql:1
-- database: trino; groups: qe, horology_functions
SELECT extract(day from TIMESTAMP '2001-08-22 03:04:05.321');

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q03.sql:1
-- database: trino; groups: tpch; tables: customer,orders,lineitem
SELECT
  l_orderkey,
  sum(l_extendedprice * (1 - l_discount)) AS revenue,
  o_orderdate,
  o_shippriority
FROM
  customer,
  orders,
  lineitem
WHERE
  c_mktsegment = 'BUILDING'
  AND c_custkey = o_custkey
  AND l_orderkey = o_orderkey
  AND o_orderdate < DATE '1995-03-15'
  AND l_shipdate > DATE '1995-03-15'
GROUP BY
  l_orderkey,
  o_orderdate,
  o_shippriority
ORDER BY
  revenue DESC,
  o_orderdate
LIMIT 10;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q13.sql:1
-- database: trino; groups: tpch; tables: customer
SELECT
  c_count,
  count(*) AS custdist
FROM (
       SELECT
         c_custkey,
         count(o_orderkey)
       FROM
         customer
         LEFT OUTER JOIN orders ON
                                  c_custkey = o_custkey
                                  AND o_comment NOT LIKE '%special%requests%'
       GROUP BY
         c_custkey
     ) AS c_orders (c_custkey, c_count)
GROUP BY
  c_count
ORDER BY
  custdist DESC,
  c_count DESC;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q19.sql:1
-- database: trino;  groups: tpch, large_query; tables: lineitem,part
SELECT sum(l_extendedprice * (1 - l_discount)) AS revenue
FROM
  lineitem,
  part
WHERE
  (
    p_partkey = l_partkey
    AND p_brand = 'Brand#12'
    AND p_container IN ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
    AND l_quantity >= 1 AND l_quantity <= 1 + 10
    AND p_size BETWEEN 1 AND 5
    AND l_shipmode IN ('AIR', 'AIR REG')
    AND l_shipinstruct = 'DELIVER IN PERSON'
  )
  OR
  (
    p_partkey = l_partkey
    AND p_brand = 'Brand#23'
    AND p_container IN ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
    AND l_quantity >= 10 AND l_quantity <= 10 + 10
    AND p_size BETWEEN 1 AND 10
    AND l_shipmode IN ('AIR', 'AIR REG')
    AND l_shipinstruct = 'DELIVER IN PERSON'
  )
  OR
  (
    p_partkey = l_partkey
    AND p_brand = 'Brand#34'
    AND p_container IN ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
    AND l_quantity >= 20 AND l_quantity <= 20 + 10
    AND p_size BETWEEN 1 AND 15
    AND l_shipmode IN ('AIR', 'AIR REG')
    AND l_shipinstruct = 'DELIVER IN PERSON'
  );

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/join/inner_join_right_outer_join.sql:1
-- database: trino; groups: join; tables: nation, region, part
SELECT p_partkey,
       n_name,
       r_name
FROM   part
       INNER JOIN nation
               ON n_regionkey = p_partkey
       RIGHT JOIN region
               ON n_nationkey = r_regionkey;

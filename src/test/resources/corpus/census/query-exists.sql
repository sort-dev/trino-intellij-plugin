-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q21.sql:1
-- database: trino; groups: tpch,large_query; tables: supplier,lineitem,orders,nation
SELECT
  s_name,
  count(*) AS numwait
FROM
  supplier,
  lineitem l1,
  orders,
  nation
WHERE
  s_suppkey = l1.l_suppkey
  AND o_orderkey = l1.l_orderkey
  AND o_orderstatus = 'F'
  AND l1.l_receiptdate > l1.l_commitdate
  AND exists(
    SELECT *
    FROM
      lineitem l2
    WHERE
      l2.l_orderkey = l1.l_orderkey
      AND l2.l_suppkey <> l1.l_suppkey
  )
  AND NOT exists(
    SELECT *
    FROM
      lineitem l3
    WHERE
      l3.l_orderkey = l1.l_orderkey
      AND l3.l_suppkey <> l1.l_suppkey
      AND l3.l_receiptdate > l3.l_commitdate
  )
  AND s_nationkey = n_nationkey
  AND n_name = 'SAUDI ARABIA'
GROUP BY
  s_name
ORDER BY
  numwait DESC,
  s_name
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/hive_tpch/q22.sql:1
-- database: trino; groups: tpch; tables: orders,customer
SELECT
  cntrycode,
  count(*)       AS numcust,
  sum(c_acctbal) AS totacctbal
FROM (
       SELECT
         substr(c_phone, 1, 2) AS cntrycode,
         c_acctbal
       FROM
         customer
       WHERE
         substr(c_phone, 1, 2) IN
         ('13', '31', '23', '29', '30', '18', '17')
         AND c_acctbal > (
           SELECT avg(c_acctbal)
           FROM
             customer
           WHERE
             c_acctbal > 0.00
             AND substr(c_phone, 1, 2) IN
                 ('13', '31', '23', '29', '30', '18', '17')
         )
         AND NOT exists(
           SELECT *
           FROM
             orders
           WHERE
             o_custkey = c_custkey
         )
     ) AS custsale
GROUP BY
  cntrycode
ORDER BY
  cntrycode;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q10.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "cd_gender"
, "cd_marital_status"
, "cd_education_status"
, "count"(*) "cnt1"
, "cd_purchase_estimate"
, "count"(*) "cnt2"
, "cd_credit_rating"
, "count"(*) "cnt3"
, "cd_dep_count"
, "count"(*) "cnt4"
, "cd_dep_employed_count"
, "count"(*) "cnt5"
, "cd_dep_college_count"
, "count"(*) "cnt6"
FROM
  customer c
, customer_address ca
, customer_demographics
WHERE ("c"."c_current_addr_sk" = "ca"."ca_address_sk")
   AND ("ca_county" IN ('Rush County', 'Toole County', 'Jefferson County', 'Dona Ana County', 'La Porte County'))
   AND ("cd_demo_sk" = "c"."c_current_cdemo_sk")
   AND (EXISTS (
   SELECT *
   FROM
     store_sales
   , date_dim
   WHERE ("c"."c_customer_sk" = "ss_customer_sk")
      AND ("ss_sold_date_sk" = "d_date_sk")
      AND ("d_year" = 2002)
      AND ("d_moy" BETWEEN 1 AND (1 + 3))
))
   AND ((EXISTS (
      SELECT *
      FROM
        web_sales
      , date_dim
      WHERE ("c"."c_customer_sk" = "ws_bill_customer_sk")
         AND ("ws_sold_date_sk" = "d_date_sk")
         AND ("d_year" = 2002)
         AND ("d_moy" BETWEEN 1 AND (1 + 3))
   ))
      OR (EXISTS (
      SELECT *
      FROM
        catalog_sales
      , date_dim
      WHERE ("c"."c_customer_sk" = "cs_ship_customer_sk")
         AND ("cs_sold_date_sk" = "d_date_sk")
         AND ("d_year" = 2002)
         AND ("d_moy" BETWEEN 1 AND (1 + 3))
   )))
GROUP BY "cd_gender", "cd_marital_status", "cd_education_status", "cd_purchase_estimate", "cd_credit_rating", "cd_dep_count", "cd_dep_employed_count", "cd_dep_college_count"
ORDER BY "cd_gender" ASC, "cd_marital_status" ASC, "cd_education_status" ASC, "cd_purchase_estimate" ASC, "cd_credit_rating" ASC, "cd_dep_count" ASC, "cd_dep_employed_count" ASC, "cd_dep_college_count" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q35.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "ca_state"
, "cd_gender"
, "cd_marital_status"
, "cd_dep_count"
, "count"(*) "cnt1"
, "min"("cd_dep_count")
, "max"("cd_dep_count")
, "avg"("cd_dep_count")
, "cd_dep_employed_count"
, "count"(*) "cnt2"
, "min"("cd_dep_employed_count")
, "max"("cd_dep_employed_count")
, "avg"("cd_dep_employed_count")
, "cd_dep_college_count"
, "count"(*) "cnt3"
, "min"("cd_dep_college_count")
, "max"("cd_dep_college_count")
, "avg"("cd_dep_college_count")
FROM
  customer c
, customer_address ca
, customer_demographics
WHERE ("c"."c_current_addr_sk" = "ca"."ca_address_sk")
   AND ("cd_demo_sk" = "c"."c_current_cdemo_sk")
   AND (EXISTS (
   SELECT *
   FROM
     store_sales
   , date_dim
   WHERE ("c"."c_customer_sk" = "ss_customer_sk")
      AND ("ss_sold_date_sk" = "d_date_sk")
      AND ("d_year" = 2002)
      AND ("d_qoy" < 4)
))
   AND ((EXISTS (
      SELECT *
      FROM
        web_sales
      , date_dim
      WHERE ("c"."c_customer_sk" = "ws_bill_customer_sk")
         AND ("ws_sold_date_sk" = "d_date_sk")
         AND ("d_year" = 2002)
         AND ("d_qoy" < 4)
   ))
      OR (EXISTS (
      SELECT *
      FROM
        catalog_sales
      , date_dim
      WHERE ("c"."c_customer_sk" = "cs_ship_customer_sk")
         AND ("cs_sold_date_sk" = "d_date_sk")
         AND ("d_year" = 2002)
         AND ("d_qoy" < 4)
   )))
GROUP BY "ca_state", "cd_gender", "cd_marital_status", "cd_dep_count", "cd_dep_employed_count", "cd_dep_college_count"
ORDER BY "ca_state" ASC, "cd_gender" ASC, "cd_marital_status" ASC, "cd_dep_count" ASC, "cd_dep_employed_count" ASC, "cd_dep_college_count" ASC
LIMIT 100;

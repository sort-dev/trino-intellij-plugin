-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q18.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "i_item_id"
, "ca_country"
, "ca_state"
, "ca_county"
, "avg"(CAST("cs_quantity" AS DECIMAL(12,2))) "agg1"
, "avg"(CAST("cs_list_price" AS DECIMAL(12,2))) "agg2"
, "avg"(CAST("cs_coupon_amt" AS DECIMAL(12,2))) "agg3"
, "avg"(CAST("cs_sales_price" AS DECIMAL(12,2))) "agg4"
, "avg"(CAST("cs_net_profit" AS DECIMAL(12,2))) "agg5"
, "avg"(CAST("c_birth_year" AS DECIMAL(12,2))) "agg6"
, "avg"(CAST("cd1"."cd_dep_count" AS DECIMAL(12,2))) "agg7"
FROM
  catalog_sales
, customer_demographics cd1
, customer_demographics cd2
, customer
, customer_address
, date_dim
, item
WHERE ("cs_sold_date_sk" = "d_date_sk")
   AND ("cs_item_sk" = "i_item_sk")
   AND ("cs_bill_cdemo_sk" = "cd1"."cd_demo_sk")
   AND ("cs_bill_customer_sk" = "c_customer_sk")
   AND ("cd1"."cd_gender" = 'F')
   AND ("cd1"."cd_education_status" = 'Unknown')
   AND ("c_current_cdemo_sk" = "cd2"."cd_demo_sk")
   AND ("c_current_addr_sk" = "ca_address_sk")
   AND ("c_birth_month" IN (1, 6, 8, 9, 12, 2))
   AND ("d_year" = 1998)
   AND ("ca_state" IN ('MS', 'IN', 'ND', 'OK', 'NM', 'VA', 'MS'))
GROUP BY ROLLUP (i_item_id, ca_country, ca_state, ca_county)
ORDER BY "ca_country" ASC, "ca_state" ASC, "ca_county" ASC, "i_item_id" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q22.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "i_product_name"
, "i_brand"
, "i_class"
, "i_category"
, "avg"("inv_quantity_on_hand") "qoh"
FROM
  inventory
, date_dim
, item
WHERE ("inv_date_sk" = "d_date_sk")
   AND ("inv_item_sk" = "i_item_sk")
   AND ("d_month_seq" BETWEEN 1200 AND (1200 + 11))
GROUP BY ROLLUP (i_product_name, i_brand, i_class, i_category)
ORDER BY "qoh" ASC, "i_product_name" ASC, "i_brand" ASC, "i_class" ASC, "i_category" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q27.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "i_item_id"
, "s_state"
, GROUPING ("s_state") "g_state"
, "avg"("ss_quantity") "agg1"
, "avg"("ss_list_price") "agg2"
, "avg"("ss_coupon_amt") "agg3"
, "avg"("ss_sales_price") "agg4"
FROM
  store_sales
, customer_demographics
, date_dim
, store
, item
WHERE ("ss_sold_date_sk" = "d_date_sk")
   AND ("ss_item_sk" = "i_item_sk")
   AND ("ss_store_sk" = "s_store_sk")
   AND ("ss_cdemo_sk" = "cd_demo_sk")
   AND ("cd_gender" = 'M')
   AND ("cd_marital_status" = 'S')
   AND ("cd_education_status" = 'College')
   AND ("d_year" = 2002)
   AND ("s_state" IN (
     'TN'
   , 'TN'
   , 'TN'
   , 'TN'
   , 'TN'
   , 'TN'))
GROUP BY ROLLUP (i_item_id, s_state)
ORDER BY "i_item_id" ASC, "s_state" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q36.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  ("sum"("ss_net_profit") / "sum"("ss_ext_sales_price")) "gross_margin"
, "i_category"
, "i_class"
, (GROUPING ("i_category") + GROUPING ("i_class")) "lochierarchy"
, "rank"() OVER (PARTITION BY (GROUPING ("i_category") + GROUPING ("i_class")), (CASE WHEN (GROUPING ("i_class") = 0) THEN "i_category" END) ORDER BY ("sum"("ss_net_profit") / "sum"("ss_ext_sales_price")) ASC) "rank_within_parent"
FROM
  store_sales
, date_dim d1
, item
, store
WHERE ("d1"."d_year" = 2001)
   AND ("d1"."d_date_sk" = "ss_sold_date_sk")
   AND ("i_item_sk" = "ss_item_sk")
   AND ("s_store_sk" = "ss_store_sk")
   AND ("s_state" IN (
     'TN'
   , 'TN'
   , 'TN'
   , 'TN'
   , 'TN'
   , 'TN'
   , 'TN'
   , 'TN'))
GROUP BY ROLLUP (i_category, i_class)
ORDER BY "lochierarchy" DESC, (CASE WHEN ("lochierarchy" = 0) THEN "i_category" END) ASC, "rank_within_parent" ASC, "i_category", "i_class"
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/convertion_functions/castAccessible.sql:1
-- database: trino; groups: qe, conversion_functions
SELECT CAST(10 as VARCHAR);

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q61.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "promotions"
, "total"
, ((CAST("promotions" AS DECIMAL(15,4)) / CAST("total" AS DECIMAL(15,4))) * 100)
FROM
  (
   SELECT "sum"("ss_ext_sales_price") "promotions"
   FROM
     store_sales
   , store
   , promotion
   , date_dim
   , customer
   , customer_address
   , item
   WHERE ("ss_sold_date_sk" = "d_date_sk")
      AND ("ss_store_sk" = "s_store_sk")
      AND ("ss_promo_sk" = "p_promo_sk")
      AND ("ss_customer_sk" = "c_customer_sk")
      AND ("ca_address_sk" = "c_current_addr_sk")
      AND ("ss_item_sk" = "i_item_sk")
      AND ("ca_gmt_offset" = -5)
      AND ("i_category" = 'Jewelry')
      AND (("p_channel_dmail" = 'Y')
         OR ("p_channel_email" = 'Y')
         OR ("p_channel_tv" = 'Y'))
      AND ("s_gmt_offset" = -5)
      AND ("d_year" = 1998)
      AND ("d_moy" = 11)
)  promotional_sales
, (
   SELECT "sum"("ss_ext_sales_price") "total"
   FROM
     store_sales
   , store
   , date_dim
   , customer
   , customer_address
   , item
   WHERE ("ss_sold_date_sk" = "d_date_sk")
      AND ("ss_store_sk" = "s_store_sk")
      AND ("ss_customer_sk" = "c_customer_sk")
      AND ("ca_address_sk" = "c_current_addr_sk")
      AND ("ss_item_sk" = "i_item_sk")
      AND ("ca_gmt_offset" = -5)
      AND ("i_category" = 'Jewelry')
      AND ("s_gmt_offset" = -5)
      AND ("d_year" = 1998)
      AND ("d_moy" = 11)
)  all_sales
ORDER BY "promotions" ASC, "total" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q90.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT (CAST("amc" AS DECIMAL(15,4)) / CAST("pmc" AS DECIMAL(15,4))) "am_pm_ratio"
FROM
  (
   SELECT "count"(*) "amc"
   FROM
     web_sales
   , household_demographics
   , time_dim
   , web_page
   WHERE ("ws_sold_time_sk" = "time_dim"."t_time_sk")
      AND ("ws_ship_hdemo_sk" = "household_demographics"."hd_demo_sk")
      AND ("ws_web_page_sk" = "web_page"."wp_web_page_sk")
      AND ("time_dim"."t_hour" BETWEEN 8 AND (8 + 1))
      AND ("household_demographics"."hd_dep_count" = 6)
      AND ("web_page"."wp_char_count" BETWEEN 5000 AND 5200)
)  "at"
, (
   SELECT "count"(*) "pmc"
   FROM
     web_sales
   , household_demographics
   , time_dim
   , web_page
   WHERE ("ws_sold_time_sk" = "time_dim"."t_time_sk")
      AND ("ws_ship_hdemo_sk" = "household_demographics"."hd_demo_sk")
      AND ("ws_web_page_sk" = "web_page"."wp_web_page_sk")
      AND ("time_dim"."t_hour" BETWEEN 19 AND (19 + 1))
      AND ("household_demographics"."hd_dep_count" = 6)
      AND ("web_page"."wp_char_count" BETWEEN 5000 AND 5200)
)  pt
ORDER BY "am_pm_ratio" ASC
LIMIT 100;

-- @stmt docs/src/main/sphinx/appendix/from-hive.md:89
SELECT CAST(5 AS DOUBLE) / 2;

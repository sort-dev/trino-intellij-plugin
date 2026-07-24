-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/functions/conditional-expressions/simple_case.sql:1
-- database: trino; groups: qe,conditional
select case when true then 33 end;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q34.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "c_last_name"
, "c_first_name"
, "c_salutation"
, "c_preferred_cust_flag"
, "ss_ticket_number"
, "cnt"
FROM
  (
   SELECT
     "ss_ticket_number"
   , "ss_customer_sk"
   , "count"(*) "cnt"
   FROM
     store_sales
   , date_dim
   , store
   , household_demographics
   WHERE ("store_sales"."ss_sold_date_sk" = "date_dim"."d_date_sk")
      AND ("store_sales"."ss_store_sk" = "store"."s_store_sk")
      AND ("store_sales"."ss_hdemo_sk" = "household_demographics"."hd_demo_sk")
      AND (("date_dim"."d_dom" BETWEEN 1 AND 3)
         OR ("date_dim"."d_dom" BETWEEN 25 AND 28))
      AND (("household_demographics"."hd_buy_potential" = '>10000')
         OR ("household_demographics"."hd_buy_potential" = 'Unknown'))
      AND ("household_demographics"."hd_vehicle_count" > 0)
      AND ((CASE WHEN ("household_demographics"."hd_vehicle_count" > 0) THEN (CAST("household_demographics"."hd_dep_count" AS DECIMAL(7,2)) / "household_demographics"."hd_vehicle_count") ELSE null END) > DECIMAL '1.2')
      AND ("date_dim"."d_year" IN (1999   , (1999 + 1)   , (1999 + 2)))
      AND ("store"."s_county" IN ('Williamson County'   , 'Williamson County'   , 'Williamson County'   , 'Williamson County'   , 'Williamson County'   , 'Williamson County'   , 'Williamson County'   , 'Williamson County'))
   GROUP BY "ss_ticket_number", "ss_customer_sk"
)  dn
, customer
WHERE ("ss_customer_sk" = "c_customer_sk")
   AND ("cnt" BETWEEN 15 AND 20)
ORDER BY "c_last_name" ASC, "c_first_name" ASC, "c_salutation" ASC, "c_preferred_cust_flag" DESC, "ss_ticket_number" ASC;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q43.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "s_store_name"
, "s_store_id"
, "sum"((CASE WHEN ("d_day_name" = 'Sunday') THEN "ss_sales_price" ELSE null END)) "sun_sales"
, "sum"((CASE WHEN ("d_day_name" = 'Monday') THEN "ss_sales_price" ELSE null END)) "mon_sales"
, "sum"((CASE WHEN ("d_day_name" = 'Tuesday') THEN "ss_sales_price" ELSE null END)) "tue_sales"
, "sum"((CASE WHEN ("d_day_name" = 'Wednesday') THEN "ss_sales_price" ELSE null END)) "wed_sales"
, "sum"((CASE WHEN ("d_day_name" = 'Thursday') THEN "ss_sales_price" ELSE null END)) "thu_sales"
, "sum"((CASE WHEN ("d_day_name" = 'Friday') THEN "ss_sales_price" ELSE null END)) "fri_sales"
, "sum"((CASE WHEN ("d_day_name" = 'Saturday') THEN "ss_sales_price" ELSE null END)) "sat_sales"
FROM
  date_dim
, store_sales
, store
WHERE ("d_date_sk" = "ss_sold_date_sk")
   AND ("s_store_sk" = "ss_store_sk")
   AND ("s_gmt_offset" = -5)
   AND ("d_year" = 2000)
GROUP BY "s_store_name", "s_store_id"
ORDER BY "s_store_name" ASC, "s_store_id" ASC, "sun_sales" ASC, "mon_sales" ASC, "tue_sales" ASC, "wed_sales" ASC, "thu_sales" ASC, "fri_sales" ASC, "sat_sales" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q50.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "s_store_name"
, "s_company_id"
, "s_street_number"
, "s_street_name"
, "s_street_type"
, "s_suite_number"
, "s_city"
, "s_county"
, "s_state"
, "s_zip"
, "sum"((CASE WHEN (("sr_returned_date_sk" - "ss_sold_date_sk") <= 30) THEN 1 ELSE 0 END)) "30 days"
, "sum"((CASE WHEN (("sr_returned_date_sk" - "ss_sold_date_sk") > 30)
   AND (("sr_returned_date_sk" - "ss_sold_date_sk") <= 60) THEN 1 ELSE 0 END)) "31-60 days"
, "sum"((CASE WHEN (("sr_returned_date_sk" - "ss_sold_date_sk") > 60)
   AND (("sr_returned_date_sk" - "ss_sold_date_sk") <= 90) THEN 1 ELSE 0 END)) "61-90 days"
, "sum"((CASE WHEN (("sr_returned_date_sk" - "ss_sold_date_sk") > 90)
   AND (("sr_returned_date_sk" - "ss_sold_date_sk") <= 120) THEN 1 ELSE 0 END)) "91-120 days"
, "sum"((CASE WHEN (("sr_returned_date_sk" - "ss_sold_date_sk") > 120) THEN 1 ELSE 0 END)) ">120 days"
FROM
  store_sales
, store_returns
, store
, date_dim d1
, date_dim d2
WHERE ("d2"."d_year" = 2001)
   AND ("d2"."d_moy" = 8)
   AND ("ss_ticket_number" = "sr_ticket_number")
   AND ("ss_item_sk" = "sr_item_sk")
   AND ("ss_sold_date_sk" = "d1"."d_date_sk")
   AND ("sr_returned_date_sk" = "d2"."d_date_sk")
   AND ("ss_customer_sk" = "sr_customer_sk")
   AND ("ss_store_sk" = "s_store_sk")
GROUP BY "s_store_name", "s_company_id", "s_street_number", "s_street_name", "s_street_type", "s_suite_number", "s_city", "s_county", "s_state", "s_zip"
ORDER BY "s_store_name" ASC, "s_company_id" ASC, "s_street_number" ASC, "s_street_name" ASC, "s_street_type" ASC, "s_suite_number" ASC, "s_city" ASC, "s_county" ASC, "s_state" ASC, "s_zip" ASC
LIMIT 100;

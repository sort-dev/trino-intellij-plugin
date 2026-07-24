-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q01.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
WITH
  customer_total_return AS (
   SELECT
     "sr_customer_sk" "ctr_customer_sk"
   , "sr_store_sk" "ctr_store_sk"
   , "sum"("sr_return_amt") "ctr_total_return"
   FROM
     store_returns
   , date_dim
   WHERE ("sr_returned_date_sk" = "d_date_sk")
      AND ("d_year" = 2000)
   GROUP BY "sr_customer_sk", "sr_store_sk"
)
SELECT "c_customer_id"
FROM
  customer_total_return ctr1
, store
, customer
WHERE ("ctr1"."ctr_total_return" > (
      SELECT ("avg"("ctr_total_return") * DECIMAL '1.2')
      FROM
        customer_total_return ctr2
      WHERE ("ctr1"."ctr_store_sk" = "ctr2"."ctr_store_sk")
   ))
   AND ("s_store_sk" = "ctr1"."ctr_store_sk")
   AND ("s_state" = 'TN')
   AND ("ctr1"."ctr_customer_sk" = "c_customer_sk")
ORDER BY "c_customer_id" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q02.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
WITH
  wscs AS (
   SELECT
     "sold_date_sk"
   , "sales_price"
   FROM
     (
      SELECT
        "ws_sold_date_sk" "sold_date_sk"
      , "ws_ext_sales_price" "sales_price"
      FROM
        web_sales
   )
UNION ALL (
      SELECT
        "cs_sold_date_sk" "sold_date_sk"
      , "cs_ext_sales_price" "sales_price"
      FROM
        catalog_sales
   ) )
, wswscs AS (
   SELECT
     "d_week_seq"
   , "sum"((CASE WHEN ("d_day_name" = 'Sunday') THEN "sales_price" ELSE null END)) "sun_sales"
   , "sum"((CASE WHEN ("d_day_name" = 'Monday') THEN "sales_price" ELSE null END)) "mon_sales"
   , "sum"((CASE WHEN ("d_day_name" = 'Tuesday') THEN "sales_price" ELSE null END)) "tue_sales"
   , "sum"((CASE WHEN ("d_day_name" = 'Wednesday') THEN "sales_price" ELSE null END)) "wed_sales"
   , "sum"((CASE WHEN ("d_day_name" = 'Thursday') THEN "sales_price" ELSE null END)) "thu_sales"
   , "sum"((CASE WHEN ("d_day_name" = 'Friday') THEN "sales_price" ELSE null END)) "fri_sales"
   , "sum"((CASE WHEN ("d_day_name" = 'Saturday') THEN "sales_price" ELSE null END)) "sat_sales"
   FROM
     wscs
   , date_dim
   WHERE ("d_date_sk" = "sold_date_sk")
   GROUP BY "d_week_seq"
)
SELECT
  "d_week_seq1"
, "round"(("sun_sales1" / "sun_sales2"), 2)
, "round"(("mon_sales1" / "mon_sales2"), 2)
, "round"(("tue_sales1" / "tue_sales2"), 2)
, "round"(("wed_sales1" / "wed_sales2"), 2)
, "round"(("thu_sales1" / "thu_sales2"), 2)
, "round"(("fri_sales1" / "fri_sales2"), 2)
, "round"(("sat_sales1" / "sat_sales2"), 2)
FROM
  (
   SELECT
     "wswscs"."d_week_seq" "d_week_seq1"
   , "sun_sales" "sun_sales1"
   , "mon_sales" "mon_sales1"
   , "tue_sales" "tue_sales1"
   , "wed_sales" "wed_sales1"
   , "thu_sales" "thu_sales1"
   , "fri_sales" "fri_sales1"
   , "sat_sales" "sat_sales1"
   FROM
     wswscs
   , date_dim
   WHERE ("date_dim"."d_week_seq" = "wswscs"."d_week_seq")
      AND ("d_year" = 2001)
)  y
, (
   SELECT
     "wswscs"."d_week_seq" "d_week_seq2"
   , "sun_sales" "sun_sales2"
   , "mon_sales" "mon_sales2"
   , "tue_sales" "tue_sales2"
   , "wed_sales" "wed_sales2"
   , "thu_sales" "thu_sales2"
   , "fri_sales" "fri_sales2"
   , "sat_sales" "sat_sales2"
   FROM
     wswscs
   , date_dim
   WHERE ("date_dim"."d_week_seq" = "wswscs"."d_week_seq")
      AND ("d_year" = (2001 + 1))
)  z
WHERE ("d_week_seq1" = ("d_week_seq2" - 53))
ORDER BY "d_week_seq1" ASC;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q11.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
WITH
  year_total AS (
   SELECT
     "c_customer_id" "customer_id"
   , "c_first_name" "customer_first_name"
   , "c_last_name" "customer_last_name"
   , "c_preferred_cust_flag" "customer_preferred_cust_flag"
   , "c_birth_country" "customer_birth_country"
   , "c_login" "customer_login"
   , "c_email_address" "customer_email_address"
   , "d_year" "dyear"
   , "sum"(("ss_ext_list_price" - "ss_ext_discount_amt")) "year_total"
   , 's' "sale_type"
   FROM
     customer
   , store_sales
   , date_dim
   WHERE ("c_customer_sk" = "ss_customer_sk")
      AND ("ss_sold_date_sk" = "d_date_sk")
   GROUP BY "c_customer_id", "c_first_name", "c_last_name", "c_preferred_cust_flag", "c_birth_country", "c_login", "c_email_address", "d_year"
UNION ALL    SELECT
     "c_customer_id" "customer_id"
   , "c_first_name" "customer_first_name"
   , "c_last_name" "customer_last_name"
   , "c_preferred_cust_flag" "customer_preferred_cust_flag"
   , "c_birth_country" "customer_birth_country"
   , "c_login" "customer_login"
   , "c_email_address" "customer_email_address"
   , "d_year" "dyear"
   , "sum"(("ws_ext_list_price" - "ws_ext_discount_amt")) "year_total"
   , 'w' "sale_type"
   FROM
     customer
   , web_sales
   , date_dim
   WHERE ("c_customer_sk" = "ws_bill_customer_sk")
      AND ("ws_sold_date_sk" = "d_date_sk")
   GROUP BY "c_customer_id", "c_first_name", "c_last_name", "c_preferred_cust_flag", "c_birth_country", "c_login", "c_email_address", "d_year"
)
SELECT
  "t_s_secyear"."customer_id"
, "t_s_secyear"."customer_first_name"
, "t_s_secyear"."customer_last_name"
, "t_s_secyear"."customer_preferred_cust_flag"
, "t_s_secyear"."customer_birth_country"
, "t_s_secyear"."customer_login"
FROM
  year_total t_s_firstyear
, year_total t_s_secyear
, year_total t_w_firstyear
, year_total t_w_secyear
WHERE ("t_s_secyear"."customer_id" = "t_s_firstyear"."customer_id")
   AND ("t_s_firstyear"."customer_id" = "t_w_secyear"."customer_id")
   AND ("t_s_firstyear"."customer_id" = "t_w_firstyear"."customer_id")
   AND ("t_s_firstyear"."sale_type" = 's')
   AND ("t_w_firstyear"."sale_type" = 'w')
   AND ("t_s_secyear"."sale_type" = 's')
   AND ("t_w_secyear"."sale_type" = 'w')
   AND ("t_s_firstyear"."dyear" = 2001)
   AND ("t_s_secyear"."dyear" = (2001 + 1))
   AND ("t_w_firstyear"."dyear" = 2001)
   AND ("t_w_secyear"."dyear" = (2001 + 1))
   AND ("t_s_firstyear"."year_total" > 0)
   AND ("t_w_firstyear"."year_total" > 0)
   AND ((CASE WHEN ("t_w_firstyear"."year_total" > 0) THEN ("t_w_secyear"."year_total" / "t_w_firstyear"."year_total") ELSE DECIMAL '0.0' END) > (CASE WHEN ("t_s_firstyear"."year_total" > 0) THEN ("t_s_secyear"."year_total" / "t_s_firstyear"."year_total") ELSE DECIMAL '0.0' END))
ORDER BY "t_s_secyear"."customer_id" ASC, "t_s_secyear"."customer_first_name" ASC, "t_s_secyear"."customer_last_name" ASC, "t_s_secyear"."customer_preferred_cust_flag" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q23_1.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
WITH
  frequent_ss_items AS (
   SELECT
     "substr"("i_item_desc", 1, 30) "itemdesc"
   , "i_item_sk" "item_sk"
   , "d_date" "solddate"
   , "count"(*) "cnt"
   FROM
     store_sales
   , date_dim
   , item
   WHERE ("ss_sold_date_sk" = "d_date_sk")
      AND ("ss_item_sk" = "i_item_sk")
      AND ("d_year" IN (2000   , (2000 + 1)   , (2000 + 2)   , (2000 + 3)))
   GROUP BY "substr"("i_item_desc", 1, 30), "i_item_sk", "d_date"
   HAVING ("count"(*) > 4)
)
, max_store_sales AS (
   SELECT "max"("csales") "tpcds_cmax"
   FROM
     (
      SELECT
        "c_customer_sk"
      , "sum"(("ss_quantity" * "ss_sales_price")) "csales"
      FROM
        store_sales
      , customer
      , date_dim
      WHERE ("ss_customer_sk" = "c_customer_sk")
         AND ("ss_sold_date_sk" = "d_date_sk")
         AND ("d_year" IN (2000      , (2000 + 1)      , (2000 + 2)      , (2000 + 3)))
      GROUP BY "c_customer_sk"
   )
)
, best_ss_customer AS (
   SELECT
     "c_customer_sk"
   , "sum"(("ss_quantity" * "ss_sales_price")) "ssales"
   FROM
     store_sales
   , customer
   WHERE ("ss_customer_sk" = "c_customer_sk")
   GROUP BY "c_customer_sk"
   HAVING ("sum"(("ss_quantity" * "ss_sales_price")) > ((50 / DECIMAL '100.0') * (
            SELECT *
            FROM
              max_store_sales
         )))
)
SELECT "sum"("sales")
FROM
  (
   SELECT ("cs_quantity" * "cs_list_price") "sales"
   FROM
     catalog_sales
   , date_dim
   WHERE ("d_year" = 2000)
      AND ("d_moy" = 2)
      AND ("cs_sold_date_sk" = "d_date_sk")
      AND ("cs_item_sk" IN (
      SELECT "item_sk"
      FROM
        frequent_ss_items
   ))
      AND ("cs_bill_customer_sk" IN (
      SELECT "c_customer_sk"
      FROM
        best_ss_customer
   ))
UNION ALL    SELECT ("ws_quantity" * "ws_list_price") "sales"
   FROM
     web_sales
   , date_dim
   WHERE ("d_year" = 2000)
      AND ("d_moy" = 2)
      AND ("ws_sold_date_sk" = "d_date_sk")
      AND ("ws_item_sk" IN (
      SELECT "item_sk"
      FROM
        frequent_ss_items
   ))
      AND ("ws_bill_customer_sk" IN (
      SELECT "c_customer_sk"
      FROM
        best_ss_customer
   ))
)
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q44.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT
  "asceding"."rnk"
, "i1"."i_product_name" "best_performing"
, "i2"."i_product_name" "worst_performing"
FROM
  (
   SELECT *
   FROM
     (
      SELECT
        "item_sk"
      , "rank"() OVER (ORDER BY "rank_col" ASC) "rnk"
      FROM
        (
         SELECT
           "ss_item_sk" "item_sk"
         , "avg"("ss_net_profit") "rank_col"
         FROM
           store_sales ss1
         WHERE ("ss_store_sk" = 4)
         GROUP BY "ss_item_sk"
         HAVING ("avg"("ss_net_profit") > (DECIMAL '0.9' * (
                  SELECT "avg"("ss_net_profit") "rank_col"
                  FROM
                    store_sales
                  WHERE ("ss_store_sk" = 4)
                     AND ("ss_addr_sk" IS NULL)
                  GROUP BY "ss_store_sk"
               )))
      )  v1
   )  v11
   WHERE ("rnk" < 11)
)  asceding
, (
   SELECT *
   FROM
     (
      SELECT
        "item_sk"
      , "rank"() OVER (ORDER BY "rank_col" DESC) "rnk"
      FROM
        (
         SELECT
           "ss_item_sk" "item_sk"
         , "avg"("ss_net_profit") "rank_col"
         FROM
           store_sales ss1
         WHERE ("ss_store_sk" = 4)
         GROUP BY "ss_item_sk"
         HAVING ("avg"("ss_net_profit") > (DECIMAL '0.9' * (
                  SELECT "avg"("ss_net_profit") "rank_col"
                  FROM
                    store_sales
                  WHERE ("ss_store_sk" = 4)
                     AND ("ss_addr_sk" IS NULL)
                  GROUP BY "ss_store_sk"
               )))
      )  v2
   )  v21
   WHERE ("rnk" < 11)
)  descending
, item i1
, item i2
WHERE ("asceding"."rnk" = "descending"."rnk")
   AND ("i1"."i_item_sk" = "asceding"."item_sk")
   AND ("i2"."i_item_sk" = "descending"."item_sk")
ORDER BY "asceding"."rnk" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q47.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
WITH
  v1 AS (
   SELECT
     "i_category"
   , "i_brand"
   , "s_store_name"
   , "s_company_name"
   , "d_year"
   , "d_moy"
   , "sum"("ss_sales_price") "sum_sales"
   , "avg"("sum"("ss_sales_price")) OVER (PARTITION BY "i_category", "i_brand", "s_store_name", "s_company_name", "d_year") "avg_monthly_sales"
   , "rank"() OVER (PARTITION BY "i_category", "i_brand", "s_store_name", "s_company_name" ORDER BY "d_year" ASC, "d_moy" ASC) "rn"
   FROM
     item
   , store_sales
   , date_dim
   , store
   WHERE ("ss_item_sk" = "i_item_sk")
      AND ("ss_sold_date_sk" = "d_date_sk")
      AND ("ss_store_sk" = "s_store_sk")
      AND (("d_year" = 1999)
         OR (("d_year" = (1999 - 1))
            AND ("d_moy" = 12))
         OR (("d_year" = (1999 + 1))
            AND ("d_moy" = 1)))
   GROUP BY "i_category", "i_brand", "s_store_name", "s_company_name", "d_year", "d_moy"
)
, v2 AS (
   SELECT
     "v1"."i_category"
   , "v1"."i_brand"
   , "v1"."s_store_name"
   , "v1"."s_company_name"
   , "v1"."d_year"
   , "v1"."d_moy"
   , "v1"."avg_monthly_sales"
   , "v1"."sum_sales"
   , "v1_lag"."sum_sales" "psum"
   , "v1_lead"."sum_sales" "nsum"
   FROM
     v1
   , v1 v1_lag
   , v1 v1_lead
   WHERE ("v1"."i_category" = "v1_lag"."i_category")
      AND ("v1"."i_category" = "v1_lead"."i_category")
      AND ("v1"."i_brand" = "v1_lag"."i_brand")
      AND ("v1"."i_brand" = "v1_lead"."i_brand")
      AND ("v1"."s_store_name" = "v1_lag"."s_store_name")
      AND ("v1"."s_store_name" = "v1_lead"."s_store_name")
      AND ("v1"."s_company_name" = "v1_lag"."s_company_name")
      AND ("v1"."s_company_name" = "v1_lead"."s_company_name")
      AND ("v1"."rn" = ("v1_lag"."rn" + 1))
      AND ("v1"."rn" = ("v1_lead"."rn" - 1))
)
SELECT *
FROM
  v2
WHERE ("d_year" = 1999)
   AND ("avg_monthly_sales" > 0)
   AND ((CASE WHEN ("avg_monthly_sales" > 0) THEN ("abs"(("sum_sales" - "avg_monthly_sales")) / "avg_monthly_sales") ELSE null END) > DECIMAL '0.1')
ORDER BY ("sum_sales" - "avg_monthly_sales") ASC, 3 ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q51.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
WITH
  web_v1 AS (
   SELECT
     "ws_item_sk" "item_sk"
   , "d_date"
   , "sum"("sum"("ws_sales_price")) OVER (PARTITION BY "ws_item_sk" ORDER BY "d_date" ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) "cume_sales"
   FROM
     web_sales
   , date_dim
   WHERE ("ws_sold_date_sk" = "d_date_sk")
      AND ("d_month_seq" BETWEEN 1200 AND (1200 + 11))
      AND ("ws_item_sk" IS NOT NULL)
   GROUP BY "ws_item_sk", "d_date"
)
, store_v1 AS (
   SELECT
     "ss_item_sk" "item_sk"
   , "d_date"
   , "sum"("sum"("ss_sales_price")) OVER (PARTITION BY "ss_item_sk" ORDER BY "d_date" ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) "cume_sales"
   FROM
     store_sales
   , date_dim
   WHERE ("ss_sold_date_sk" = "d_date_sk")
      AND ("d_month_seq" BETWEEN 1200 AND (1200 + 11))
      AND ("ss_item_sk" IS NOT NULL)
   GROUP BY "ss_item_sk", "d_date"
)
SELECT *
FROM
  (
   SELECT
     "item_sk"
   , "d_date"
   , "web_sales"
   , "store_sales"
   , "max"("web_sales") OVER (PARTITION BY "item_sk" ORDER BY "d_date" ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) "web_cumulative"
   , "max"("store_sales") OVER (PARTITION BY "item_sk" ORDER BY "d_date" ASC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) "store_cumulative"
   FROM
     (
      SELECT
        (CASE WHEN ("web"."item_sk" IS NOT NULL) THEN "web"."item_sk" ELSE "store"."item_sk" END) "item_sk"
      , (CASE WHEN ("web"."d_date" IS NOT NULL) THEN "web"."d_date" ELSE "store"."d_date" END) "d_date"
      , "web"."cume_sales" "web_sales"
      , "store"."cume_sales" "store_sales"
      FROM
        (web_v1 web
      FULL JOIN store_v1 store ON ("web"."item_sk" = "store"."item_sk")
         AND ("web"."d_date" = "store"."d_date"))
   )  x
)  y
WHERE ("web_cumulative" > "store_cumulative")
ORDER BY "item_sk" ASC, "d_date" ASC
LIMIT 100;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/tpcds/q53.sql:1
-- database: trino_tpcds; groups: tpcds; requires: io.trino.tempto.fulfillment.table.hive.tpcds.ImmutableTpcdsTablesRequirements
SELECT *
FROM
  (
   SELECT
     "i_manufact_id"
   , "sum"("ss_sales_price") "sum_sales"
   , "avg"("sum"("ss_sales_price")) OVER (PARTITION BY "i_manufact_id") "avg_quarterly_sales"
   FROM
     item
   , store_sales
   , date_dim
   , store
   WHERE ("ss_item_sk" = "i_item_sk")
      AND ("ss_sold_date_sk" = "d_date_sk")
      AND ("ss_store_sk" = "s_store_sk")
      AND ("d_month_seq" IN (1200   , (1200 + 1)   , (1200 + 2)   , (1200 + 3)   , (1200 + 4)   , (1200 + 5)   , (1200 + 6)   , (1200 + 7)   , (1200 + 8)   , (1200 + 9)   , (1200 + 10)   , (1200 + 11)))
      AND ((("i_category" IN ('Books'         , 'Children'         , 'Electronics'))
            AND ("i_class" IN ('personal'         , 'portable'         , 'reference'         , 'self-help'))
            AND ("i_brand" IN ('scholaramalgamalg #14'         , 'scholaramalgamalg #7'         , 'exportiunivamalg #9'         , 'scholaramalgamalg #9')))
         OR (("i_category" IN ('Women'         , 'Music'         , 'Men'))
            AND ("i_class" IN ('accessories'         , 'classical'         , 'fragrances'         , 'pants'))
            AND ("i_brand" IN ('amalgimporto #1'         , 'edu packscholar #1'         , 'exportiimporto #1'         , 'importoamalg #1'))))
   GROUP BY "i_manufact_id", "d_qoy"
)  tmp1
WHERE ((CASE WHEN ("avg_quarterly_sales" > 0) THEN ("abs"((CAST("sum_sales" AS DECIMAL(38,4)) - "avg_quarterly_sales")) / "avg_quarterly_sales") ELSE null END) > DECIMAL '0.1')
ORDER BY "avg_quarterly_sales" ASC, "sum_sales" ASC, "i_manufact_id" ASC
LIMIT 100;

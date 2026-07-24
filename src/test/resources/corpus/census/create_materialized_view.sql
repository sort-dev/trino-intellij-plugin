-- @stmt docs/src/main/sphinx/sql/create-materialized-view.md:90
CREATE MATERIALIZED VIEW cancelled_orders
AS
    SELECT orderkey, totalprice
    FROM orders
    WHERE orderstatus = 3;

-- @stmt docs/src/main/sphinx/sql/create-materialized-view.md:101
CREATE OR REPLACE MATERIALIZED VIEW order_totals_by_date
AS
    SELECT orderdate, sum(totalprice) AS price
    FROM orders
    GROUP BY orderdate;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7809
CREATE MATERIALIZED VIEW a AS SELECT * FROM t;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7924
CREATE MATERIALIZED VIEW a WHEN STALE FAIL AS SELECT * FROM t;

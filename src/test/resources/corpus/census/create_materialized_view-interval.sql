-- @stmt docs/src/main/sphinx/sql/create-materialized-view.md:132
CREATE MATERIALIZED VIEW orders_summary
GRACE PERIOD INTERVAL '1' HOUR
WHEN STALE FAIL
AS
    SELECT orderdate, sum(totalprice) AS price
    FROM orders
    GROUP BY orderdate;

-- @stmt docs/src/main/sphinx/sql/create-materialized-view.md:144
CREATE MATERIALIZED VIEW orders_summary
GRACE PERIOD INTERVAL '1' HOUR
WHEN STALE INLINE
AS
    SELECT orderdate, sum(totalprice) AS price
    FROM orders
    GROUP BY orderdate;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:7888
CREATE MATERIALIZED VIEW a GRACE PERIOD INTERVAL '2' DAY AS SELECT * FROM t;

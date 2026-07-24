-- @stmt docs/src/main/sphinx/sql/create-materialized-view.md:112
CREATE MATERIALIZED VIEW orders_nation_mkgsegment
COMMENT 'Orders with nation and market segment data'
WITH ( partitioning = ARRAY['mktsegment', 'nationkey'] )
AS
    SELECT o.*, c.nationkey, c.mktsegment
    FROM orders AS o
    JOIN customer AS c
    ON o.custkey = c.custkey;

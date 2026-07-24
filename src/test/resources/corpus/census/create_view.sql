-- @stmt docs/src/main/sphinx/sql/create-view.md:43
CREATE VIEW test AS
SELECT orderkey, orderstatus, totalprice / 2 AS half
FROM orders;

-- @stmt docs/src/main/sphinx/sql/create-view.md:51
CREATE VIEW test_with_comment
COMMENT 'A view to keep track of orders.'
AS
SELECT orderkey, orderstatus, totalprice
FROM orders;

-- @stmt docs/src/main/sphinx/sql/create-view.md:61
CREATE VIEW orders_by_date AS
SELECT orderdate, sum(totalprice) AS price
FROM orders
GROUP BY orderdate;

-- @stmt docs/src/main/sphinx/sql/create-view.md:70
CREATE OR REPLACE VIEW test AS
SELECT orderkey, orderstatus, totalprice / 4 AS quarter
FROM orders;

-- @stmt docs/src/main/sphinx/sql/delete.md:25
DELETE FROM lineitem
WHERE orderkey IN (SELECT orderkey FROM orders WHERE priority = 'LOW');

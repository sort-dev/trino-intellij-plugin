-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/set_operation/except.sql:4
SELECT n_name FROM nation WHERE n_nationkey = 17
EXCEPT
SELECT n_name FROM nation WHERE n_regionkey = 2
UNION
(SELECT n_name FROM nation WHERE n_regionkey = 2
INTERSECT
SELECT n_name FROM nation WHERE n_nationkey > 15);

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/set_operation/except.sql:16
SELECT n_name FROM nation WHERE n_nationkey = 17
EXCEPT
SELECT n_name FROM nation WHERE n_regionkey = 2
UNION ALL
(SELECT n_name FROM nation WHERE n_regionkey = 2
INTERSECT
SELECT n_name FROM nation WHERE n_nationkey > 15);

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/set_operation/except.sql:28
SELECT id_employee FROM workers
EXCEPT
SELECT department FROM workers where department IS NOT NULL;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/set_operation/intersect.sql:4
SELECT n_name FROM nation WHERE n_nationkey = 17
INTERSECT
SELECT n_name FROM nation WHERE n_regionkey = 1
UNION
SELECT n_name FROM nation WHERE n_regionkey = 2;

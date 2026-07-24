-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/array_functions/checkArrayFunctionsRegistered.sql:1
-- database: trino; groups: qe, array_functions, functions
show functions;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/binary_functions/checkBinaryFunctionsRegistered.sql:1
-- database: trino; groups: qe, binary_functions, functions
show functions;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/catalog/showFunctions.sql:1
-- database: trino; groups: base_sql; queryType: SELECT
show functions;

-- @stmt testing/trino-product-tests/src/main/resources/sql-tests/testcases/horology_functions/checkHorologyFunctionsRegistered.sql:1
-- database: trino; groups: qe, horology_functions, functions
show functions;

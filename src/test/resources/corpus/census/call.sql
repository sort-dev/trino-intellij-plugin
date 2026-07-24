-- @stmt docs/src/main/sphinx/connector/delta-lake.md:528
CALL examplecatalog.system.example_procedure();

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:542
CALL example.system.register_table(schema_name => 'testdb', table_name => 'customer_orders', table_location => 's3://my-bucket/a/path');

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:559
CALL example.system.unregister_table(schema_name => 'testdb', table_name => 'customer_orders');

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:586
CALL example.system.vacuum('exampleschemaname', 'exampletablename', '7d');

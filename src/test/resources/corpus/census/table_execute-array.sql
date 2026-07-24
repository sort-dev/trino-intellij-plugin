-- @stmt docs/src/main/sphinx/connector/iceberg.md:729
ALTER TABLE example.lakehouse.iceberg_customer_orders 
EXECUTE add_files_from_table(
    schema_name => 'legacy',
    table_name => 'customer_orders',
    partition_filter => map(ARRAY['region', 'country'], ARRAY['ASIA', 'JAPAN']));

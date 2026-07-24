-- @stmt docs/src/main/sphinx/connector/delta-lake.md:743
ALTER TABLE test_table EXECUTE optimize
WHERE "$path" <> 'skipping-file-path';

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:748
-- optimze files smaller than 1MB
ALTER TABLE test_table EXECUTE optimize
WHERE "$file_size" <= 1024 * 1024;

-- @stmt docs/src/main/sphinx/connector/iceberg.md:707
ALTER TABLE example.lakehouse.iceberg_customer_orders 
EXECUTE add_files_from_table(
    schema_name => 'legacy',
    table_name => 'customer_orders');

-- @stmt docs/src/main/sphinx/connector/iceberg.md:718
ALTER TABLE iceberg_customer_orders 
EXECUTE add_files_from_table(
    schema_name => 'legacy',
    table_name => 'customer_orders');

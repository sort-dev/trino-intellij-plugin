-- @stmt docs/src/main/sphinx/connector/delta-lake.md:738
ALTER TABLE test_table EXECUTE optimize
WHERE "$file_modified_time" > date_trunc('day', CURRENT_TIMESTAMP);

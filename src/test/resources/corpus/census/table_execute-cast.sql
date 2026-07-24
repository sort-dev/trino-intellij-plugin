-- @stmt docs/src/main/sphinx/connector/iceberg.md:925
ALTER TABLE test_table EXECUTE optimize
WHERE CAST(timestamp_tz AS DATE) > DATE '2021-12-31';

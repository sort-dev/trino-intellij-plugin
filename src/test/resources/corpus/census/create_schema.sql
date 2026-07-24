-- @stmt docs/src/main/sphinx/connector/delta-lake.md:659
CREATE SCHEMA example.example_schema
WITH (location = 's3://my-bucket/a/path');

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:668
CREATE SCHEMA example.example_schema;

-- @stmt docs/src/main/sphinx/connector/hive.md:637
CREATE SCHEMA example.web
WITH (location = 's3://my-bucket/');

-- @stmt docs/src/main/sphinx/connector/iceberg.md:511
CREATE SCHEMA example.example_s3_schema
WITH (location = 's3://my-bucket/a/path/');

-- @stmt docs/src/main/sphinx/sql/create-catalog.md:49
CREATE CATALOG tpch USING tpch;

-- @stmt docs/src/main/sphinx/sql/create-catalog.md:55
CREATE CATALOG brain USING memory
WITH ("memory.max-data-per-node" = '128MB');

-- @stmt docs/src/main/sphinx/sql/create-catalog.md:66
CREATE CATALOG example USING postgresql
WITH (
  "connection-url" = 'jdbc:pg:localhost:5432',
  "connection-user" = '${ENV:POSTGRES_USER}',
  "connection-password" = '${ENV:POSTGRES_PASSWORD}',
  "case-insensitive-name-matching" = 'true'
);

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:3204
CREATE CATALOG test USING conn;

-- @stmt docs/src/main/sphinx/connector/iceberg.md:1074
ALTER TABLE table_name SET PROPERTIES format_version = 2;

-- @stmt docs/src/main/sphinx/sql/alter-materialized-view.md:52
ALTER MATERIALIZED VIEW people SET PROPERTIES x = 'y';

-- @stmt docs/src/main/sphinx/sql/alter-materialized-view.md:59
ALTER MATERIALIZED VIEW people SET PROPERTIES foo = 123, "foo bar" = 456;

-- @stmt docs/src/main/sphinx/sql/alter-materialized-view.md:65
ALTER MATERIALIZED VIEW people SET PROPERTIES x = DEFAULT;

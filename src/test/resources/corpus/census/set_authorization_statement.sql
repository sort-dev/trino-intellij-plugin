-- @stmt docs/src/main/sphinx/sql/alter-materialized-view.md:71
ALTER MATERIALIZED VIEW people SET AUTHORIZATION alice;

-- @stmt docs/src/main/sphinx/sql/alter-schema.md:25
ALTER SCHEMA web SET AUTHORIZATION alice;

-- @stmt docs/src/main/sphinx/sql/alter-schema.md:31
ALTER SCHEMA web SET AUTHORIZATION ROLE PUBLIC;

-- @stmt docs/src/main/sphinx/sql/alter-table.md:161
ALTER TABLE people SET AUTHORIZATION alice;

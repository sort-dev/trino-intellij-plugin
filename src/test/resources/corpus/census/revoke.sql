-- @stmt docs/src/main/sphinx/sql/revoke.md:34
REVOKE INSERT, SELECT ON orders FROM alice;

-- @stmt docs/src/main/sphinx/sql/revoke.md:40
REVOKE DELETE ON SCHEMA finance FROM bob;

-- @stmt docs/src/main/sphinx/sql/revoke.md:46
REVOKE GRANT OPTION FOR SELECT ON nation FROM ROLE PUBLIC;

-- @stmt docs/src/main/sphinx/sql/revoke.md:52
REVOKE ALL PRIVILEGES ON test FROM alice;

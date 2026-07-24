-- @stmt docs/src/main/sphinx/sql/grant.md:33
GRANT INSERT, SELECT ON orders TO alice;

-- @stmt docs/src/main/sphinx/sql/grant.md:39
GRANT DELETE ON SCHEMA finance TO bob;

-- @stmt docs/src/main/sphinx/sql/grant.md:45
GRANT SELECT ON nation TO alice WITH GRANT OPTION;

-- @stmt docs/src/main/sphinx/sql/grant.md:51
GRANT SELECT ON orders TO ROLE PUBLIC;

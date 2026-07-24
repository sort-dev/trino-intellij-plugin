-- @stmt docs/src/main/sphinx/sql/deny.md:27
DENY INSERT, SELECT ON orders TO alice;

-- @stmt docs/src/main/sphinx/sql/deny.md:33
DENY DELETE ON SCHEMA finance TO bob;

-- @stmt docs/src/main/sphinx/sql/deny.md:39
DENY SELECT ON orders TO ROLE PUBLIC;

-- @stmt docs/src/main/sphinx/sql/deny.md:45
DENY INSERT ON BRANCH audit IN orders TO alice;

-- @stmt docs/src/main/sphinx/release/release-0.124.md:41
ALTER TABLE verifier_queries ADD COLUMN test_postqueries text;

-- @stmt docs/src/main/sphinx/release/release-0.124.md:42
ALTER TABLE verifier_queries ADD COLUMN test_prequeries text;

-- @stmt docs/src/main/sphinx/release/release-0.124.md:43
ALTER TABLE verifier_queries ADD COLUMN control_postqueries text;

-- @stmt docs/src/main/sphinx/release/release-0.124.md:44
ALTER TABLE verifier_queries ADD COLUMN control_prequeries text;

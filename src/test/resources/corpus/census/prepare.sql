-- @stmt docs/src/main/sphinx/sql/describe-input.md:20
PREPARE my_select1 FROM
SELECT ? FROM nation WHERE regionkey = ? AND name < ?;

-- @stmt docs/src/main/sphinx/sql/describe-input.md:40
PREPARE my_select2 FROM
SELECT * FROM nation;

-- @stmt docs/src/main/sphinx/sql/describe-output.md:21
PREPARE my_select1 FROM
SELECT * FROM nation;

-- @stmt docs/src/main/sphinx/sql/describe-output.md:42
PREPARE my_select2 FROM
SELECT count(*) as my_count, 1+2 FROM nation;

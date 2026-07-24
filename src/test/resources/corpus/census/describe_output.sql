-- @stmt docs/src/main/sphinx/sql/describe-output.md:26
DESCRIBE OUTPUT my_select1;

-- @stmt docs/src/main/sphinx/sql/describe-output.md:47
DESCRIBE OUTPUT my_select2;

-- @stmt docs/src/main/sphinx/sql/describe-output.md:66
DESCRIBE OUTPUT my_create;

-- @stmt docs/src/main/sphinx/sql/describe-output.md:77
DESCRIBE OUTPUT (SELECT *, n_name AS "name" FROM nation);

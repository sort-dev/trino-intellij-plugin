-- @stmt docs/src/main/sphinx/functions/table.md:211
PREPARE stmt FROM
SELECT * FROM TABLE(my_function(row_count => ? + 1, column_count => ?));

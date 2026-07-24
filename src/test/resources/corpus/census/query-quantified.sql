-- @stmt docs/src/main/sphinx/functions/comparison.md:214
SELECT 'hello' = ANY (VALUES 'hello', 'world');

-- @stmt docs/src/main/sphinx/functions/comparison.md:214
-- true

SELECT 21 < ALL (VALUES 19, 20, 21);

-- @stmt docs/src/main/sphinx/functions/comparison.md:216
-- false

SELECT 42 >= SOME (SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43);

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestStatementBuilder.java:323
SELECT * FROM table1 WHERE a >= ALL (VALUES 2, 3, 4);

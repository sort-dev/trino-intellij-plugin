-- @stmt docs/src/main/sphinx/functions/string.md:312
SELECT trim('!' FROM '!foo!');

-- @stmt docs/src/main/sphinx/functions/string.md:312
-- 'foo'
SELECT trim(LEADING FROM '  abcd');

-- @stmt docs/src/main/sphinx/functions/string.md:313
-- 'abcd'
SELECT trim(BOTH '$' FROM '$var$');

-- @stmt docs/src/main/sphinx/functions/string.md:314
-- 'var'
SELECT trim(TRAILING 'ER' FROM upper('worker'));

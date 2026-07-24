-- @stmt docs/src/main/sphinx/functions/aggregate.md:210
SELECT listagg(value, ',') WITHIN GROUP (ORDER BY value) csv_value
FROM (VALUES 'a', 'c', 'b') t(value);

-- @stmt docs/src/main/sphinx/functions/aggregate.md:225
SELECT listagg(CAST(v AS VARCHAR), ',') WITHIN GROUP (ORDER BY v) csv_value
FROM (VALUES 1, 3, 2) t(v);

-- @stmt docs/src/main/sphinx/functions/aggregate.md:241
SELECT listagg(value, ',' ON OVERFLOW ERROR) WITHIN GROUP (ORDER BY value) csv_value
FROM (VALUES 'a', 'b', 'c') t(value);

-- @stmt docs/src/main/sphinx/functions/aggregate.md:250
SELECT listagg(value, ',' ON OVERFLOW TRUNCATE '.....' WITH COUNT) WITHIN GROUP (ORDER BY value)
FROM (VALUES 'a', 'b', 'c') t(value);

-- @stmt docs/src/main/sphinx/functions/json.md:1888
-- JSON '{"k1":1,"k2":23,"k3":456}'

SELECT CAST(CAST(ROW(123, 'abc', true) AS
            ROW(v1 BIGINT, v2 VARCHAR, v3 BOOLEAN)) AS JSON);

-- @stmt docs/src/main/sphinx/functions/json.md:1938
-- {k1=1, k2=23, k3=456}

SELECT CAST(JSON '{"v1":123,"v2":"abc","v3":true}' AS
            ROW(v1 BIGINT, v2 VARCHAR, v3 BOOLEAN));

-- @stmt docs/src/main/sphinx/functions/json.md:1942
-- {v1=123, v2=abc, v3=true}

SELECT CAST(JSON '[123,"abc",true]' AS
            ROW(v1 BIGINT, v2 VARCHAR, v3 BOOLEAN));

-- @stmt docs/src/main/sphinx/sql/select.md:301
SELECT (CAST(ROW(1, true) AS ROW(field1 bigint, field2 boolean))).* AS (alias1, alias2);

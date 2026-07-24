-- @stmt docs/src/main/sphinx/connector/faker.md:230
SELECT JSON_OBJECT(KEY currency VALUE price) AS complex
FROM faker.default.prices
LIMIT 3;

-- @stmt docs/src/main/sphinx/functions/json.md:896
SELECT
      id,
      json_exists(
                  description,
                  'lax $.children[*]?(@ > 10)'
                 ) AS children_above_ten
FROM customers;

-- @stmt docs/src/main/sphinx/functions/json.md:917
SELECT
      id,
      json_exists(
                  description,
                  'strict $.children[2]?(@ > 10)'
                  UNKNOWN ON ERROR
                 ) AS child_3_above_ten
FROM customers;

-- @stmt docs/src/main/sphinx/functions/json.md:1026
SELECT
      id,
      json_query(
                 description,
                 'lax $.children'
                ) AS children
FROM customers;

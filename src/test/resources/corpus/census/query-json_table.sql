-- @stmt docs/src/main/sphinx/functions/json.md:1431
SELECT
      *
FROM
      json_table(
                '[
                  {"id":1,"name":"Africa","wikiDataId":"Q15"},
                  {"id":2,"name":"Americas","wikiDataId":"Q828"},
                  {"id":3,"name":"Asia","wikiDataId":"Q48"},
                  {"id":4,"name":"Europe","wikiDataId":"Q51"}
                ]',
                'strict $' COLUMNS (
                  NESTED PATH 'strict $[*]' COLUMNS (
                    id integer PATH 'strict $.id',
                    name varchar PATH 'strict $.name',
                    wiki_data_id varchar PATH 'strict $."wikiDataId"'
                  )
                )
              );

-- @stmt docs/src/main/sphinx/functions/json.md:1470
SELECT
      *
FROM
      json_table(
                '[
                    {"continent": "Asia", "countries": [
                        {"name": "Japan", "population": 125.7},
                        {"name": "Thailand", "population": 71.6}
                    ]},
                    {"continent": "Europe", "countries": [
                        {"name": "France", "population": 67.4},
                        {"name": "Germany", "population": 83.2}
                    ]}
                ]',
                'lax $' COLUMNS (
                    NESTED PATH 'lax $[*]' COLUMNS (
                        continent varchar PATH 'lax $.continent',
                        NESTED PATH 'lax $.countries[*]' COLUMNS (
                            country varchar PATH 'lax $.name',
                            population double PATH 'lax $.population'
                        )
                    )
                ));

-- @stmt docs/src/main/sphinx/functions/json.md:1506
SELECT
      *
FROM
      JSON_TABLE(
                '[]',
                'lax $' AS "root_path"
                COLUMNS(
                    a varchar(1) PATH 'lax "A"',
                    NESTED PATH 'lax $[*]' AS "nested_path"
                            COLUMNS (b varchar(1) PATH 'lax "B"'))
                PLAN ("root_path" OUTER "nested_path")
                );

-- @stmt docs/src/main/sphinx/functions/json.md:1528
SELECT
      *
FROM
      JSON_TABLE(
                '[]',
                'lax $' AS "root_path"
                COLUMNS(
                    a varchar(1) PATH 'lax "A"',
                    NESTED PATH 'lax $[*]' AS "nested_path"
                            COLUMNS (b varchar(1) PATH 'lax "B"'))
                PLAN ("root_path" INNER "nested_path")
                );

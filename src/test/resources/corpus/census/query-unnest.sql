-- @stmt docs/src/main/sphinx/appendix/from-hive.md:122
SELECT student, score
FROM tests
CROSS JOIN UNNEST(scores) AS t (score);

-- @stmt docs/src/main/sphinx/sql/select.md:1225
SELECT * FROM UNNEST(ARRAY[1,2]) AS t(number);

-- @stmt docs/src/main/sphinx/sql/select.md:1239
SELECT * FROM UNNEST(
        map_from_entries(
            ARRAY[
                ('SQL',1974),
                ('Java', 1995)
            ]
        )
) AS t(language, first_appeared_year);

-- @stmt docs/src/main/sphinx/sql/select.md:1261
SELECT *
FROM UNNEST(
        ARRAY[
            ROW('Java',  1995),
            ROW('SQL' , 1974)],
        ARRAY[
            ROW(false),
            ROW(true)]
) as t(language,first_appeared_year,declarative);

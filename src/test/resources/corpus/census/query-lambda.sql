-- @stmt docs/src/main/sphinx/functions/aggregate.md:651
SELECT id, reduce_agg(value, 0, (a, b) -> a + b, (a, b) -> a + b)
FROM (
    VALUES
        (1, 3),
        (1, 4),
        (1, 5),
        (2, 6),
        (2, 7)
) AS t(id, value)
GROUP BY id;

-- @stmt docs/src/main/sphinx/functions/aggregate.md:660
-- (1, 12)
-- (2, 13)

SELECT id, reduce_agg(value, 1, (a, b) -> a * b, (a, b) -> a * b)
FROM (
    VALUES
        (1, 3),
        (1, 4),
        (1, 5),
        (2, 6),
        (2, 7)
) AS t(id, value)
GROUP BY id;

-- @stmt docs/src/main/sphinx/functions/array.md:181
SELECT array_sort(ARRAY[3, 2, 5, 1, 2],
                  (x, y) -> IF(x < y, 1, IF(x = y, 0, -1)));

-- @stmt docs/src/main/sphinx/functions/array.md:182
-- [5, 3, 2, 2, 1]

SELECT array_sort(ARRAY['bc', 'ab', 'dc'],
                  (x, y) -> IF(x < y, 1, IF(x = y, 0, -1)));

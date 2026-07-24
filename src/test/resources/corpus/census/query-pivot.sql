-- @stmt docs/src/main/sphinx/sql/pivot.md:44
SELECT *
FROM sales PIVOT (
    sum(amount) AS total
    FOR month IN (1 AS jan, 2 AS feb, 3 AS mar)
    GROUP BY region
    );

-- @stmt docs/src/main/sphinx/sql/pivot.md:192
SELECT p.r, p.jan, p.feb
FROM sales PIVOT (
    sum(amount) FOR month IN (1 AS jan, 2 AS feb)
    GROUP BY region
    ) AS p (r, jan, feb);

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:9593
SELECT * FROM t PIVOT (sum(amount) FOR month IN (1, 2));

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:9607
SELECT * FROM t PIVOT (sum(amount) AS total, avg(amount) AS mean FOR month IN (1 AS jan));

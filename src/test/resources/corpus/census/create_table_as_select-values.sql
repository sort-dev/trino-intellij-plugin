-- @stmt docs/src/main/sphinx/connector/iceberg.md:575
CREATE TABLE yearly_clicks (
    year,
    clicks
)
WITH (
    partitioning = ARRAY['year']
)
AS VALUES
    (2021, 10000),
    (2022, 20000);

-- @stmt docs/src/main/sphinx/sql/values.md:55
CREATE TABLE example AS
SELECT * FROM (
    VALUES
        (1, 'a'),
        (2, 'b'),
        (3, 'c')
) AS t (id, name);

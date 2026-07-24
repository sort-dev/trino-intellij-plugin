-- @stmt docs/src/main/sphinx/optimizer/cost-in-explain.md:21
EXPLAIN SELECT comment FROM tpch.sf1.nation WHERE nationkey > 3;

-- @stmt docs/src/main/sphinx/optimizer/pushdown.md:83
EXPLAIN
SELECT regionkey, count(*)
FROM nation
GROUP BY regionkey;

-- @stmt docs/src/main/sphinx/optimizer/pushdown.md:278
EXPLAIN SELECT id, name
FROM postgresql.public.company
ORDER BY id
LIMIT 5;

-- @stmt docs/src/main/sphinx/sql/explain.md:59
EXPLAIN (TYPE LOGICAL) SELECT regionkey, count(*) FROM nation GROUP BY 1;

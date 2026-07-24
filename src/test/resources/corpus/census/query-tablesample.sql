-- @stmt docs/src/main/sphinx/sql/select.md:1202
SELECT *
FROM users TABLESAMPLE BERNOULLI (50);

-- @stmt docs/src/main/sphinx/sql/select.md:1205
SELECT *
FROM users TABLESAMPLE SYSTEM (75);

-- @stmt docs/src/main/sphinx/sql/select.md:1212
SELECT o.*, i.*
FROM orders o TABLESAMPLE SYSTEM (10)
JOIN lineitem i TABLESAMPLE BERNOULLI (40)
  ON o.orderkey = i.orderkey;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestStatementBuilder.java:63
select * from t x tablesample system (10);

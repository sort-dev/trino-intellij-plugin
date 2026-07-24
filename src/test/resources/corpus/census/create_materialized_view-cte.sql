-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:8049
CREATE OR REPLACE MATERIALIZED VIEW catalog.schema.matview COMMENT 'A partitioned materialized view'
WITH (partitioned_by = ARRAY ['dateint'])
 AS WITH a (t, u) AS (SELECT * FROM x), b AS (SELECT * FROM a) TABLE b;

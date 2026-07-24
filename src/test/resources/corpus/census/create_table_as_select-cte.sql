-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4379
CREATE TABLE foo
AS
( WITH t(x) AS (VALUES 1)
TABLE t )
WITH NO DATA;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4387
CREATE TABLE foo
AS
WITH t(x) AS (VALUES 1)
TABLE t
WITH NO DATA;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4395
CREATE TABLE foo(a)
AS
( WITH t(x) AS (VALUES 1)
TABLE t )
WITH NO DATA;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4403
CREATE TABLE foo(a)
AS
WITH t(x) AS (VALUES 1)
TABLE t
WITH NO DATA;

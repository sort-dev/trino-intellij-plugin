-- @stmt docs/src/main/sphinx/sql/set-path.md:26
SET PATH example.system;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5970
SET PATH iLikeToEat.apples, andBananas;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5975
SET PATH "schemas,with"."grammar.in", "their!names";

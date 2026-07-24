-- @stmt docs/src/main/sphinx/sql/alter-schema.md:19
ALTER SCHEMA web RENAME TO traffic;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:3297
ALTER SCHEMA foo RENAME TO bar;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:3303
ALTER SCHEMA foo.bar RENAME TO baz;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:3309
ALTER SCHEMA "awesome schema"."awesome table" RENAME TO "even more awesome table";

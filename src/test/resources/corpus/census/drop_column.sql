-- @stmt docs/src/main/sphinx/sql/alter-table.md:123
ALTER TABLE users DROP COLUMN zip;

-- @stmt docs/src/main/sphinx/sql/alter-table.md:130
ALTER TABLE IF EXISTS users DROP COLUMN IF EXISTS zip;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5375
ALTER TABLE foo.t DROP COLUMN c;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:5382
ALTER TABLE "t x" DROP COLUMN "c d";

-- @stmt docs/src/main/sphinx/sql/comment.md:18
COMMENT ON TABLE users IS 'master table';

-- @stmt docs/src/main/sphinx/sql/comment.md:24
COMMENT ON VIEW users IS 'master view';

-- @stmt docs/src/main/sphinx/sql/comment.md:30
COMMENT ON COLUMN users.name IS 'full name';

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParser.java:4867
COMMENT ON TABLE a IS 'test';

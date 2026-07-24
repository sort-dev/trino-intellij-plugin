-- @stmt docs/src/main/sphinx/sql/create-function.md:27
CREATE FUNCTION example.default.meaning_of_life()
  RETURNS bigint
  BEGIN
    RETURN 42;
  END;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:334 (wrapped)
WITH
FUNCTION test()
RETURNS int
BEGIN
  DECLARE r int DEFAULT 0;
  abc: LOOP
    SET r = r + 1;
    LEAVE abc;
  END LOOP;
  abc: LOOP
    SET r = r + 1;
    LEAVE abc;
  END LOOP;
  RETURN r;
END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/repeat.md:36 (wrapped)
WITH
FUNCTION test_repeat(a bigint)
  RETURNS bigint
  BEGIN
    REPEAT
      SET a = a + 1;
    UNTIL a >= 10
    END REPEAT;
    RETURN a;
  END
SELECT 1;

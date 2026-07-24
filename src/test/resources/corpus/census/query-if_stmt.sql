-- @stmt docs/src/main/sphinx/udf/sql/examples.md:215 (wrapped)
WITH
FUNCTION fib(n bigint)
RETURNS bigint
BEGIN
  DECLARE a, b bigint DEFAULT 1;
  DECLARE c bigint;
  IF n <= 2 THEN
    RETURN 1;
  END IF;
  WHILE n > 2 DO
    SET n = n - 1;
    SET c = a + b;
    SET a = b;
    SET b = c;
  END WHILE;
  RETURN c;
END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:259 (wrapped)
WITH
FUNCTION labels()
RETURNS bigint
BEGIN
  DECLARE a, b int DEFAULT 0;
  top: WHILE a < 10 DO
    SET a = a + 1;
    IF a < 3 THEN
      ITERATE top;
    END IF;
    SET b = b + 1;
    IF a > 6 THEN
      LEAVE top;
    END IF;
  END WHILE;
  RETURN b;
END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:284 (wrapped)
WITH
FUNCTION power(n int, p int)
RETURNS int
  BEGIN
    DECLARE r int DEFAULT n;
    top: LOOP
      IF p <= 1 THEN
        LEAVE top;
      END IF;
      SET r = r * n;
      SET p = p - 1;
    END LOOP;
    RETURN r;
  END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:313 (wrapped)
WITH
FUNCTION test_repeat_continue()
RETURNS bigint
BEGIN
  DECLARE a int DEFAULT 0;
  DECLARE b int DEFAULT 0;
  top: REPEAT
    SET a = a + 1;
    IF a <= 3 THEN
      ITERATE top;
    END IF;
    SET b = b + 1;
  UNTIL a >= 10
  END REPEAT;
  RETURN b;
END
SELECT 1;

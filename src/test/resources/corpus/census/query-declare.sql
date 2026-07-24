-- @stmt docs/src/main/sphinx/udf/sql/begin.md:40 (wrapped)
WITH
FUNCTION meaning_of_life()
  RETURNS integer
  BEGIN
    DECLARE a integer DEFAULT 6;
    DECLARE b integer DEFAULT 7;
    RETURN a * b;
  END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:122 (wrapped)
WITH
FUNCTION times_ninety_nine(a bigint)
RETURNS bigint
BEGIN
  DECLARE x bigint DEFAULT CAST(99 AS bigint);
  RETURN x * a;
END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:358 (wrapped)
WITH
FUNCTION test()
RETURNS bigint
BEGIN
  DECLARE r bigint DEFAULT 0;
  BEGIN
    DECLARE x varchar DEFAULT 'hello';
    SET r = r + length(x);
  END;
  BEGIN
    DECLARE x array(int) DEFAULT array[1, 2, 3];
    SET r = r + cardinality(x);
  END;
  RETURN r;
END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:753 (wrapped)
WITH
FUNCTION ascii_bar(value DOUBLE)
RETURNS VARCHAR
BEGIN
  DECLARE max_width DOUBLE DEFAULT 40.0;
  RETURN array_join(
    repeat('█',
        greatest(0, CAST(floor(max_width * value) AS integer) - 1)), '')
        || ARRAY[' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█']
        [cast((value % (cast(1 as double) / max_width)) * max_width * 8 + 1 as int)];
END
SELECT 1;

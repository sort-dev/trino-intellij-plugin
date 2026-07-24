-- @stmt docs/src/main/sphinx/udf/sql/case.md:44 (wrapped)
WITH
FUNCTION simple_case(a bigint)
  RETURNS varchar
  BEGIN
    CASE a
      WHEN 0 THEN RETURN 'zero';
      WHEN 1 THEN RETURN 'one';
      ELSE RETURN 'more than one or negative';
    END CASE;
    RETURN NULL;
  END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:142 (wrapped)
WITH
FUNCTION simple_case(a bigint)
RETURNS varchar
BEGIN
  CASE a
    WHEN 0 THEN RETURN 'zero';
    WHEN 1 THEN RETURN 'one';
    WHEN 10 THEN RETURN 'ten';
    WHEN 20 THEN RETURN 'twenty';
    ELSE RETURN 'other';
  END CASE;
  RETURN NULL;
END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:173 (wrapped)
WITH
FUNCTION search_case(a bigint, b bigint)
RETURNS varchar
BEGIN
  CASE
    WHEN a = 0 THEN RETURN 'zero';
    WHEN b = 1 THEN RETURN 'one';
    WHEN a = DECIMAL '10.0' THEN RETURN 'ten';
    WHEN b = 20.0E0 THEN RETURN 'twenty';
    ELSE RETURN 'other';
  END CASE;
  RETURN NULL;
END
SELECT 1;

-- @stmt docs/src/main/sphinx/udf/sql/examples.md:561 (wrapped)
WITH
FUNCTION strtrunc(input VARCHAR)
RETURNS VARCHAR
BEGIN
    CASE WHEN length(input) > 60
    THEN
        RETURN substr(input, 1, 30) || ' ... ' || substr(input, length(input) - 25);
    ELSE
        RETURN input;
    END CASE;
    RETURN input;
END
SELECT 1;

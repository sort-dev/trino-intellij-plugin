-- @stmt docs/src/main/sphinx/sql/select.md:113
WITH 
  FUNCTION hello(name varchar)
    RETURNS varchar
    RETURN format('Hello %s!', name),
  FUNCTION bye(name varchar)
    RETURNS varchar
    RETURN format('Bye %s!', name)
SELECT hello('Finn') || ' and ' || bye('Joe');

-- @stmt docs/src/main/sphinx/udf/function.md:96
WITH FUNCTION meaning_of_life()
  RETURNS BIGINT
  RETURN 42
SELECT meaning_of_life();

-- @stmt docs/src/main/sphinx/udf/introduction.md:30
WITH
  FUNCTION doubleup(x integer)
    RETURNS integer
    RETURN x * 2
SELECT doubleup(21);

-- @stmt docs/src/main/sphinx/udf/introduction.md:50
WITH
  FUNCTION doubleup(x integer)
    RETURNS integer
    RETURN x * 2,
  FUNCTION doubleupplusone(x integer)
    RETURNS integer
    RETURN doubleup(x) + 1
SELECT doubleupplusone(21);

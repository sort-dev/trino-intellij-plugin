-- @stmt docs/src/main/sphinx/sql/create-function.md:39
CREATE FUNCTION meaning_of_life() RETURNS bigint RETURN 42;

-- @stmt docs/src/main/sphinx/udf/function.md:82
CREATE FUNCTION example.default.meaning_of_life()
  RETURNS BIGINT
  RETURN 42;

-- @stmt docs/src/main/sphinx/udf/python/examples.md:50
CREATE FUNCTION example.default.answer()
  RETURNS int
  LANGUAGE PYTHON
  WITH (handler='theanswer')
  AS $$
  def theanswer():
      return 42
  $$;

-- @stmt docs/src/main/sphinx/udf/python/examples.md:80
CREATE FUNCTION answer()
  RETURNS int
  LANGUAGE PYTHON
  WITH (handler='theanswer')
  AS $$
  def theanswer():
      return 42
  $$;

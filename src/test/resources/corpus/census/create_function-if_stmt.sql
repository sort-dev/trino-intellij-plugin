-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParserRoutines.java:170
CREATE FUNCTION fib(n bigint)
RETURNS bigint
BEGIN
  DECLARE a bigint DEFAULT 1;
  DECLARE b bigint DEFAULT 1;
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
END;

-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestSqlParserRoutines.java:227
CREATE FUNCTION CustomerLevel(p_creditLimit DOUBLE)
RETURNS varchar
RETURNS NULL ON NULL INPUT
SECURITY DEFINER
BEGIN
  DECLARE lvl VarChar;
  IF p_creditLimit > 50000 THEN
    SET lvl = 'PLATINUM';
  ELSEIF (p_creditLimit <= 50000 AND p_creditLimit >= 10000) THEN
    SET lvl = 'GOLD';
  ELSEIF p_creditLimit < 10000 THEN
    SET lvl = 'SILVER';
  END IF;
  RETURN (lvl);
END;

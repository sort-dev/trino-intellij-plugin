-- @stmt core/trino-parser/src/test/java/io/trino/sql/parser/TestStatementBuilder.java:260
create view foo as with a as (select 123) select * from a;

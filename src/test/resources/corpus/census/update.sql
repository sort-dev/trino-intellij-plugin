-- @stmt docs/src/main/sphinx/connector/delta-lake.md:1048
UPDATE test_schema.pages
    SET domain = 'domain4'
    WHERE views = 2;

-- @stmt docs/src/main/sphinx/sql/update.md:25
UPDATE
  purchases
SET
  status = 'OVERDUE'
WHERE
  ship_date IS NULL;

-- @stmt docs/src/main/sphinx/sql/update.md:36
UPDATE
  customers
SET
  account_manager = 'John Henry',
  assign_date = now();

-- @stmt docs/src/main/sphinx/sql/update.md:63
UPDATE
  purchases @ audit
SET
  status = 'OVERDUE'
WHERE
  ship_date IS NULL;

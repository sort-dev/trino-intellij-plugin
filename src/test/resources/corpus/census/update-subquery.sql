-- @stmt docs/src/main/sphinx/sql/update.md:46
UPDATE
  new_hires
SET
  manager = (
    SELECT
      e.name
    FROM
      employees e
    WHERE
      e.employee_id = new_hires.manager_id
  );

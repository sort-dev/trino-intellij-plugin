-- @stmt docs/src/main/sphinx/connector/bigquery.md:539
SELECT
  *
FROM
  TABLE(
    example.system.query(
      query => 'SELECT
        manager_id, STRING_AGG(employee_id)
      FROM
        company.employees
      GROUP BY
        manager_id'
    )
  );

-- @stmt docs/src/main/sphinx/connector/cassandra.md:342
SELECT
  *
FROM
  TABLE(
    example.system.query(
      query => 'SELECT
        *
      FROM
        tpch.nation'
    )
  );

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:992
SELECT
  *
FROM
  TABLE(
    system.table_changes(
      schema_name => 'test_schema',
      table_name => 'tableName',
      since_version => 0
    )
  );

-- @stmt docs/src/main/sphinx/connector/delta-lake.md:1056
SELECT
  *
FROM
  TABLE(
    system.table_changes(
      schema_name => 'test_schema',
      table_name => 'pages',
      since_version => 1
    )
  )
ORDER BY _commit_version ASC;

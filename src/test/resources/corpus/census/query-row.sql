-- @stmt docs/src/main/sphinx/develop/supporting-merge.md:108
SELECT
 CASE
   WHEN present AND s.address = 'Berkeley' THEN
       -- Null values for delete; present=true; operation DELETE=2, case_number=0
       row(null, null, null, true, 2, 0)
   WHEN present AND s.customer = 'Joe Shmoe' THEN
       -- Update column values; present=true; operation UPDATE=3, case_number=1
       row(t.customer, t.purchases + 100.0, t.address, true, 3, 1)
   WHEN present THEN
       -- Update column values; present=true; operation UPDATE=3, case_number=2
       row(t.customer, s.purchases + t.purchases, s.address, true, 3, 2)
   WHEN (present IS NULL) THEN
       -- Insert column values; present=false; operation INSERT=1, case_number=3
       row(s.customer, s.purchases, s.address, false, 1, 3)
   ELSE
       -- Null values for no case matched; present=false; operation=-1,
       --     case_number=-1
       row(null, null, null, false, -1, -1)
 END
 FROM (SELECT *, true AS present FROM target_table) t
   RIGHT JOIN source_table s ON s.customer = t.customer;

-- @stmt docs/src/main/sphinx/functions/datetime.md:53
SELECT (DATE '2020-01-01', DATE '2020-06-01') OVERLAPS (DATE '2020-05-01', DATE '2020-12-31');

-- @stmt docs/src/main/sphinx/functions/datetime.md:53
-- true

SELECT (DATE '2020-01-01', DATE '2020-03-01') OVERLAPS (DATE '2020-05-01', DATE '2020-07-01');

-- @stmt docs/src/main/sphinx/functions/row.md:8
SELECT ROW::fields(row('hello' as greeting, 'world' as planet));

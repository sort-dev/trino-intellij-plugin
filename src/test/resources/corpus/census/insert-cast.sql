-- @stmt docs/src/main/sphinx/functions/hyperloglog.md:42
INSERT INTO visit_summaries
SELECT visit_date, cast(approx_set(user_id) AS varbinary)
FROM user_visits
GROUP BY visit_date;

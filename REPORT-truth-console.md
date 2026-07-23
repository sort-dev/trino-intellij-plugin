# Truth Battery — Auth Matrix + Console

Measured 2026-07-23 against a live **Trino 483** coordinator (no auth) through **io.trino:trino-jdbc:483**.
Pinned by `TrinoAuthMatrixTest` and `TrinoConsoleTruthTest`. Both suites Assume-out unless
`-Dtrino.live.url=...` is set, so the committed run stays offline-deterministic.

## Auth matrix (validates the `config/trino-brikk-drivers.xml` auth-model comment)

| # | Client setup | Outcome | `current_user` |
|---|---|---|---|
| 1 | `?sessionUser=X`, no user/password props | **connects** | `X` |
| 2 | `user=X` property alone | **connects** | `X` |
| 3 | `user=A` prop + `?sessionUser=B` | **connects** | **`B`** — sessionUser overrides the connection user |
| 4 | `user=X` + `password=...`, **no SSL** | **REFUSED** | err: `TLS/SSL is required for authentication with username and password` |
| 5 | `user=''` (empty) + `?sessionUser=X` | **REFUSED** | err: `Connection property user value is empty` |

**Verdict — the no-TLS template is correct.** Map the dialog's User field onto the `sessionUser=` URL
param and **keep Password empty**: [1] proves `sessionUser` alone authenticates; [4] proves a password
without SSL is hard-refused (the design justification — Password MUST stay empty on this template);
[5] proves an empty `user` *property* is rejected *before* sessionUser is read, so a blank field must
**omit** the property (feed `sessionUser` only), never send `user=`. The TLS template keeps real
user/password over SSL. `current_user` reflects `sessionUser` when both are present ([3]).

## Cancel — client AND server-side (the doris "never trust the client alone" check)

- **Client: WORKS.** A `sf10` self cross join with a `% 7` predicate (forced real work) is aborted by
  `Statement.cancel()` from another thread: raises `java.sql.SQLException("Error fetching results")`
  within ~tens of ms of the cancel, and the **connection stays usable** (`SELECT 42` -> 42 on a fresh
  statement).
- **Server-side: WORKS (verified, not assumed).** The cancelled query — located in
  `system.runtime.queries` by a unique embedded marker — reaches **`FAILED`** and stays there (polled
  ~10s), never lingering `RUNNING`. trino-jdbc cancels via a `DELETE` to the query REST endpoint, so
  the engine genuinely stops. This is the opposite of the doris KILL-QUERY silent-no-op and the quack
  cancel no-op: **Trino cancel is real, end to end.**

## Grid-rendering verdicts (executeQuery -> non-empty ResultSet)

| Statement | cols x rows |
|---|---|
| `SHOW CATALOGS` | 1 x 5 |
| `SHOW SCHEMAS FROM tpch` | 1 x 10 |
| `DESCRIBE tpch.tiny.orders` | 4 x 9 |
| `EXPLAIN SELECT * FROM tpch.tiny.orders` | 1 x 1 (plan text) |
| `EXPLAIN (TYPE VALIDATE) SELECT ...` (valid) | 1 x 1 -> `true` |
| `EXPLAIN (TYPE VALIDATE) SELECT no_such_col ...` | error: `Column 'no_such_col' cannot be resolved` |

**`EXPLAIN (TYPE VALIDATE)` is the future live-validation seam:** it does full **name resolution**
(not just syntax) over JDBC and returns `true` on success / a precise semantic error on failure —
exactly what an in-IDE "validate against the live cluster" hook needs.

## Transaction facts

- **autocommit default = true.**
- **`START TRANSACTION` / `SELECT` / `COMMIT`** round-trips (read-only transaction) via plain `execute()`.
- **The `memory` connector rejects non-autocommit writes:** `START TRANSACTION; INSERT INTO memory...`
  fails with `Catalog only supports writes using autocommit: memory`. A console must submit memory
  writes in autocommit; other connectors' transactional support varies by connector.

## Session props

- **`SET SESSION query_max_run_time = '2h'`** then **`SHOW SESSION LIKE 'query_max_run_time'`** shows
  `Value = 2h`. trino-jdbc applies `SET SESSION` **client-side** to subsequent statements on the same
  connection — session state is per-connection, as a console expects.

## Container recipe (standing dev dependency)

    docker run -d --name trino -p 18080:8080 trinodb/trino:483

Run the live battery against it:

    ./gradlew test -Dtrino.live.url='jdbc:trino://localhost:18080?sessionUser=truth'

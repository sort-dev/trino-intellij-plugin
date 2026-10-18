# SQL Dialect for Trino

A full-featured **Trino SQL dialect** for DataGrip and IntelliJ-family IDEs — validated by
**Trino's own parser** (io.trino:trino-parser, bundled, currently version **483**), so editor
errors and reporting are correct.

> **In the IDE, use the `Trino (sort.dev)` dialect.**

Part of our SQL-tooling family alongside:

* [**Trino - Doris connector** allowing Trino to access your Doris data](https://github.com/brikk/trino-doris-connector)
* [**Trino native DuckLake Catalog** with full Read/Write support](https://github.com/brikk/ducklake-integrations/tree/main/jvm/trino-ducklake)
* [**Trino Duckbridge** - remote access to DuckDB via Quack](https://github.com/brikk/duckbridge)
* [**SQL Dialect Transpiler** for various datalake dialects and DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/32900-sql-transpiler)
* [**DuckDB SQL** (embedded+Quack) for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/33098-sql-dialect-for-duckdb-embedded--quack-)
* [**Doris SQL Dialect** for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/32777-sql-dialect-for-apache-doris)
* [**brikk-house** - Data engineering platform](https://github.com/brikk/brikk-house) (coming soon)

## Why

Stock IDEs treat Trino/Presto/Athena as *generic* data sources with the Generic SQL editor:
lambdas, `TABLE()` function arguments, `MATCH_RECOGNIZE`, `SHOW`/`USE`/session statements and
catalog-qualified DDL red-flag or break statement boundaries. This plugin bundles the engine's
own grammar, so the editor's error authority IS the engine's.

## What it does

- **Correct statement & run-block boundaries** across the Trino statement surface — including
  SQL routines (`CREATE FUNCTION ... BEGIN ... END` with inner semicolons), `MATCH_RECOGNIZE`,
  `PIVOT`, JSON clause functions, `GRANT`/`DENY`, branch DDL, prepared statements.
- **Engine-exact error checking with zero setup**: every statement is validated by the bundled
  **trino-parser 483**, with squiggles at the engine's exact line/column and the engine's own
  messages. No data source, no server, no configuration needed.
- **Function completion**: 442 functions harvested live from Trino 483 `SHOW FUNCTIONS`
  (scalar / aggregate / window / table, kind icons, return-type hints) + 83 reserved words.
- **A working schema tree**: connectors appear as catalogs (tpch, memory, ...).
- **Completion that fills the tree in as you type**: catalogs list on connect; drilling into a
  catalog or schema (`hive.`, `hive.web.`) introspects just that namespace, one level at a time —
  so a `hive`/`iceberg` catalog with thousands of tables is never bulk-loaded.
- **Query cancel that works**: verified client- AND server-side — a cancelled query reaches
  FAILED in `system.runtime.queries`, not a silent client-only cancel.

## SQL coverage — measured, not claimed

Coverage is scored against a census harvested from **Trino's own repository at tag 483**
(product tests, documentation SQL, parser tests — every statement pre-graded valid by the
bundled trino-parser): **150/150 syntax families / 490 statements parse clean (100%), zero
degraded shapes**. The census regenerates mechanically per engine bump, so coverage is
re-proven per Trino version, not asserted once.

## Connecting (important — Trino auth model)

- **`default` connection type (no TLS)** — Trino refuses username/password auth without SSL, so
  the **User field feeds `sessionUser=`** (your session identity) and the **Password field must
  stay empty**. Port 8080.
- **`SSL` connection type** — port **8443** (Trino's HTTPS default). Turn on TLS from the
  **SSH/SSL tab** (check *Use SSL*; for a private CA, add the cert file there) — User & Password
  auth then works over the encrypted connection.
- **Catalog & schema are not part of the connection** — the data source is just host + port +
  auth. Pick catalogs in your SQL (`USE catalog.schema`, fully-qualified names) or the Schemas tab.
- **Database tree**: comes from JDBC metadata via the generic introspector (the plugin routes
  Trino data sources there automatically); if a data source was created before the plugin was
  installed, tick **Use JDBC-based introspector** in its Options tab.

## Built-in PIPE queries

Enable **Settings > Tools > Database > Trino PIPE** for the current project. No SQL Transpiler
installation is required. The setting defaults off; changing it reparses open SQL files.

```sql
FROM tpch.tiny.orders
|> WHERE orderstatus = 'O'
|> AGGREGATE count(*) AS orders GROUP BY custkey
|> ORDER BY orders DESC
|> LIMIT 10
```

Normal Execute lowers PIPE to Trino SQL through the usual console. Selection, script execution,
parameters, result tabs and console ownership are preserved. Every selected PIPE is validated
before the first request; unsupported/lossy translation and invalid generated SQL stop execution.

Alt+Enter or the **Trino PIPE** context menu provides generated-SQL preview and
run-to-caret-stage. Stage keywords and known prior-stage columns complete. Run-to-stage selects
top-level stages; a caret inside a nested pipeline is refused rather than silently running later
inner stages. Normal Execute lowers nested pipelines. Console server errors are shown against
the generated SQL.

## Credits

- **[Trino](https://trino.io)** — the query engine this plugin exists for, and the publisher of
  the parser it bundles. We are an independent project: not affiliated with, and not endorsed by,
  the Trino Software Foundation. "Trino" is their trademark, used only to identify the engine.
- Full third-party attributions (trino-parser, StarRocks-lineage parsing techniques):
  THIRD_PARTY_NOTICES.md.

## License

Apache-2.0. Independent community plugin by Sortdev SRL.

Building from source and contributing: [dev-docs/CONTRIBUTING.md](dev-docs/CONTRIBUTING.md).

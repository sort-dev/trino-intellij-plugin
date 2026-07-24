# SQL Dialect for Trino

A full featured **Trino SQL dialect** for DataGrip and IntelliJ-family IDEs — validated by **Trino's own
parser** (io.trino:trino-parser, bundled, currently version **483**), so editor errors and reporting
are correct and accurate.

Part of our SQL-tooling family alongside:

* [**Doris SQL Dialect** for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/32777-sql-dialect-for-apache-doris)
* [**SQL Dialect Transpiler** for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/32900-sql-transpiler)
* [**DuckDB SQL** (embedded+Quack) for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/33098-sql-dialect-for-duckdb-embedded--quack-)
* [**Trino native DuckLake Catalog** with full Read/Write support](https://github.com/brikk/ducklake-integrations/tree/main/jvm/trino-ducklake)
* [**Trino Duckbridge** - remote access to DuckDB via Quack](https://github.com/brikk/duckbridge)
* [**Trino - Doris connector**](https://github.com/brikk/trino-doris-connector)
* [**brikk-house** - Data engineering platform](https://github.com/brikk/brikk-house) (coming soon)

> **In the IDE, use the `Trino (sort.dev)` dialect.** 

## SQL coverage — measured, not claimed

Full **Trino SQL Dialect** with coverage scored against a census harvested from **Trino's own repository at tag 483**
(product tests, documentation SQL, parser tests — every statement pre-graded valid by the
bundled trino-parser): **150/150 syntax families / 490 statements parse clean (100%), zero
degraded shapes**. The census regenerates mechanically per engine bump
(`./gradlew harvestCensus`), so coverage is re-proven per Trino version, not asserted once.

## Why

Stock IDEs treat Trino/Presto/Athena as *generic* data sources with the Generic SQL editor:
lambdas, `TABLE()` function arguments, `MATCH_RECOGNIZE`, `SHOW`/`USE`/session statements and
catalog-qualified DDL red-flag or break statement boundaries. Trino publishes its parser as a
plain Maven artifact, versioned with every release — which makes the strongest form of our
playbook possible: the editor's error authority IS the engine's grammar, bundled.


## What it does

- **Correct statement & run-block boundaries** across the Trino statement surface — including
  SQL routines (`CREATE FUNCTION ... BEGIN ... END` with inner semicolons), `MATCH_RECOGNIZE`,
  `PIVOT`, JSON clause functions, `GRANT`/`DENY`, branch DDL, prepared statements.
- **Engine-exact error checking with zero setup**: every statement is validated by the bundled
  **trino-parser 483** — Trino's own grammar — with squiggles at the engine's exact line/column
  and the engine's own messages. No data source, no server, no configuration needed.
- **Function completion**: 442 functions harvested live from Trino 483 `SHOW FUNCTIONS`
  (scalar / aggregate / window / table, kind icons, return-type hints) + 83 reserved words from
  the parser's own list.
- **A working schema tree**: connectors appear as catalogs (tpch, memory, ...), via the generic
  JDBC introspector — deliberately forced for this dbms (Trino reports its release number as the
  JDBC major version, which would otherwise select PostgreSQL's introspector against a server
  with no pg_catalog; see REPORT-truth-tree.md).
- **Completion that fills the tree in as you type**: catalogs list on connect; drilling into a
  catalog or schema (`hive.`, `hive.web.`) introspects just that namespace, one level at a time —
  so a `hive`/`iceberg` catalog with thousands of tables is never bulk-loaded.
- **Query cancel that works**: verified client- AND server-side against a live 483 (the query
  reaches FAILED in system.runtime.queries — not a silent client-only cancel).

## Connecting (important — Trino auth model)

- **`default` connection type (no TLS)** — Trino refuses username/password auth without SSL (the
  JDBC driver hard-errors), so the **User field feeds `sessionUser=`** (your session identity) and
  the **Password field must stay empty**. Port 8080.
- **`SSL` connection type** — port **8443** (Trino's HTTPS default). Turn on TLS from the
  **SSH/SSL tab** (check *Use SSL*; for a private CA, add the cert file there) — it's mapped to
  trino-jdbc's real SSL settings, and User & Password auth then works over the encrypted
  connection. (TLS is deliberately *not* baked into the URL — that would collide with the tab.)
- **Catalog & schema are not part of the connection** — the data source is just host + port + auth.
  Pick catalogs in your SQL (`USE catalog.schema`, fully-qualified names) or the Schemas tab.
- **Database tree**: comes from JDBC metadata via the generic introspector (the plugin routes
  Trino data sources there automatically — Trino has no `pg_catalog` for a native one); if a data
  source was created before the plugin was installed, tick **Use JDBC-based introspector** in its
  Options tab.

## Building from source

```bash
./gradlew buildPlugin   # → build/distributions/trino-intellij-plugin.zip
./gradlew test          # boot + driver-facts + parser-authority (census scoreboard arrives with Stage 1)
```

DataGrip 2026.1 SDK (auto-downloaded), Kotlin 2.4.10, JVM 21. One artifact serves platform
2026.1 + 2026.2 (builds 261/262).

## Credits

- **[Trino](https://trino.io)** — the query engine this plugin exists for, and the publisher of
  the parser it bundles. We are an independent project: not affiliated with, and not endorsed by,
  the Trino Software Foundation. "Trino" is their trademark, used only to identify the engine.
- Full third-party attributions (trino-parser, StarRocks-lineage parsing techniques):
  THIRD_PARTY_NOTICES.md.

## License

Apache-2.0. Independent community plugin by Sortdev SRL.

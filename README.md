# SQL Dialect for Trino

A real **Trino SQL dialect** for DataGrip and IntelliJ-family IDEs — validated by **Trino's own
parser** (io.trino:trino-parser, bundled, currently **483**), so editor errors are the engine's
errors.

Built on the architecture of our shipped
[SQL Dialect for Apache Doris](https://github.com/sort-dev/doris-intellij-plugin) and
[SQL Dialect for DuckDB](https://github.com/sort-dev/duckdb-intellij-plugin) plugins, over the
SQL tooling in [brikk-house](https://github.com/brikk/brikk-house) (more or less — the Trino
parse authority here is Trino's own parser).

> **In the IDE, use the `Trino (sort.dev)` dialect.** It's picked automatically for the bundled
> Trino data source; for a plain SQL file, set it via the dialect switcher in the editor's
> status bar, or **Settings → Languages & Frameworks → SQL Dialects**.

## SQL coverage — measured, not claimed

Coverage is scored against a census harvested from **Trino's own repository at tag 483**
(product tests, documentation SQL, parser tests — every statement pre-graded valid by the
bundled trino-parser): **150/150 syntax families / 490 statements parse clean (100%), zero
degraded shapes**. The census regenerates mechanically per engine bump
(`./gradlew harvestCensus`), so coverage is re-proven per Trino version, not asserted once.

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
- **Query cancel that works**: verified client- AND server-side against a live 483 (the query
  reaches FAILED in system.runtime.queries — not a silent client-only cancel).

## Why

Stock IDEs treat Trino/Presto/Athena as *generic* data sources with the Generic SQL editor:
lambdas, `TABLE()` function arguments, `MATCH_RECOGNIZE`, `SHOW`/`USE`/session statements and
catalog-qualified DDL red-flag or break statement boundaries. Trino publishes its parser as a
plain Maven artifact, versioned with every release — which makes the strongest form of our
playbook possible: the editor's error authority IS the engine's grammar, bundled.

## Connecting (important — Trino auth model)

- **No TLS ⇒ no password.** Trino refuses username/password auth without SSL (the JDBC driver
  hard-errors). On the default template the **User field feeds `sessionUser=`** (your identity
  for the session) and the **Password field must stay empty**.
- **TLS template**: real user/password auth over SSL (port 443 default).
- **Database tree**: comes from JDBC metadata via the generic introspector (the plugin routes
  Trino data sources there automatically — Trino has no `pg_catalog` for a native one); if a data
  source was created before the plugin was installed, tick **Use JDBC-based introspector** in its
  Options tab.

## Building from source

```bash
./gradlew buildPlugin   # → build/distributions/trino-intellij-plugin.zip
./gradlew test          # boot + driver-facts + parser-authority (census scoreboard arrives with Stage 1)
```

DataGrip 2026.1 SDK (auto-downloaded), Kotlin 2.3.0, JVM 21. One artifact serves platform
2026.1 + 2026.2 (builds 261/262).

## Credits

- **[Trino](https://trino.io)** — the query engine this plugin exists for, and the publisher of
  the parser it bundles. We are an independent project: not affiliated with, and not endorsed by,
  the Trino Software Foundation. "Trino" is their trademark, used only to identify the engine.
- Full third-party attributions (trino-parser, StarRocks-lineage parsing techniques):
  THIRD_PARTY_NOTICES.md.

## License

Apache-2.0. Independent community plugin by Sortdev SRL.

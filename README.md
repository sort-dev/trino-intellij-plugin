# Trino support for DataGrip & IntelliJ IDEA

A plugin that adds a real [Trino](https://trino.io) SQL dialect to DataGrip and IntelliJ IDEA
Ultimate. It checks your SQL with Trino's own parser, gets statement boundaries right, completes
Trino functions, and loads big catalogs one level at a time.

> [!IMPORTANT]
> **Not affiliated with Trino.** This is an independent plugin by Sortdev SRL. It is not developed,
> endorsed or sponsored by the Trino Software Foundation. "Trino" is a trademark of the Trino
> Software Foundation, used here only to name the query engine this plugin works with.

## Why

Out of the box, the IDE treats Trino, Presto and Athena as generic data sources with a generic SQL
editor. Lambdas, `TABLE()` arguments, `MATCH_RECOGNIZE`, `SHOW`/`USE`/session statements and
catalog-qualified DDL show false errors or break statement boundaries. This plugin bundles Trino's
own parser, so the errors you see are the ones Trino would report.

## Features

- **Correct statement boundaries** across Trino SQL, including SQL routines
  (`CREATE FUNCTION … BEGIN … END` with semicolons inside), `MATCH_RECOGNIZE`, `PIVOT`, JSON
  functions, `GRANT`/`DENY`, branch DDL and prepared statements.
- **Trino's own error checking.** The bundled Trino 483 parser checks every statement and
  underlines errors at the exact line and column, with Trino's own messages. This works without a
  data source or server.
- **Function completion** for 442 Trino functions (scalar, aggregate, window and table), with
  return types, plus 83 reserved words.
- **A working database tree.** Connectors show up as catalogs (`tpch`, `memory`, …).
- **Completion that loads as you type.** Catalogs list on connect. Typing `hive.` or `hive.web.`
  loads just that catalog or schema, so a catalog with thousands of tables never loads all at once.
- **Query cancel that works.** Stop cancels the query on the server, not just in the IDE. A
  cancelled query shows as FAILED in `system.runtime.queries`.
- **Trino PIPE (optional).** Write pipe-syntax queries and run them on Trino. See
  [Trino PIPE](#trino-pipe).

All 490 statements in a test set taken from Trino's own repository (150 syntax families) parse
cleanly. The set is rebuilt for each Trino version, so coverage is re-checked on every upgrade.

## Requirements

- DataGrip or IntelliJ IDEA Ultimate, **2026.1 to 2026.3**.
- A Trino server for the database tree, completion from your catalogs, and running queries. Error
  checking works without one.

## Install

**From the JetBrains Marketplace (recommended).** Open **Settings → Plugins → Marketplace**, search
for *SQL Dialect for Trino*, install it and restart the IDE.

**From a file.** Download the zip from the
[Releases](https://github.com/sort-dev/trino-intellij-plugin/releases) page, then use
**Settings → Plugins → ⚙ → Install Plugin from Disk…** and restart.

## Getting started

1. Add a Trino data source and connect.
2. Use the **Trino (sort.dev)** dialect for your consoles and `.sql` files.
3. If the data source existed before you installed the plugin, tick **Use JDBC-based
   introspector** in its **Options** tab so the database tree loads.

## Connecting

Trino's login rules depend on whether the connection uses TLS.

- **Without TLS** (the `default` connection type, port 8080), Trino refuses passwords. The **User**
  field sets your session user and the **Password** field must stay empty.
- **With TLS** (the `SSL` connection type, port 8443), tick **Use SSL** in the **SSH/SSL** tab. Add
  your certificate file there if you use a private CA. User and password login then works.
- The data source holds only host, port and login. Pick catalogs in your SQL (`USE catalog.schema`
  or fully qualified names) or in the **Schemas** tab.

## Trino PIPE

Write queries in [pipe syntax](https://cloud.google.com/bigquery/docs/pipe-syntax-guide) and run
them on Trino.

```sql
FROM tpch.tiny.orders
|> WHERE orderstatus = 'O'
|> AGGREGATE count(*) AS orders GROUP BY custkey
|> ORDER BY orders DESC
|> LIMIT 10
```

When you run it, the plugin converts the pipe query to plain Trino SQL and sends that through the
normal console. Selections, scripts, parameters and result tabs work as usual.

**Turn it on** in **Settings → Tools → Database → Trino PIPE**. It's off by default and is set per
project. Open SQL files update right away.

What you get:

- Completion for stage keywords and for the columns coming from earlier stages.
- <kbd>Alt</kbd>+<kbd>Enter</kbd> or right-click → **Trino PIPE** to preview the generated SQL or
  run the pipeline up to the stage at your cursor.
- The plugin checks every pipe query before sending anything. If the conversion isn't supported,
  would change the meaning, or produces invalid SQL, nothing runs.

## Known limitations

- Run-to-stage works on top-level stages only. With the cursor inside a nested pipeline, the
  plugin refuses instead of running the wrong stages. Normal Execute handles nested pipelines.
- Server errors for pipe queries point at the generated SQL, not your pipe query.

## Related plugins and tools

- [Apache Doris SQL](https://plugins.jetbrains.com/plugin/32777-sql-dialect-for-apache-doris)
  dialect for JetBrains IDEs.
- [ClickHouse SQL](https://github.com/sort-dev/clickhouse-intellij-plugin) dialect for JetBrains
  IDEs.
- [DuckDB SQL](https://plugins.jetbrains.com/plugin/33098-sql-dialect-for-duckdb-embedded--quack-)
  dialect for JetBrains IDEs, for embedded DuckDB and Quack servers.
- [SQL Transpiler](https://plugins.jetbrains.com/plugin/32900-sql-transpiler) converts SQL between
  dialects in JetBrains IDEs. You don't need it for Trino PIPE.
- [Trino Doris connector](https://github.com/brikk/trino-doris-connector) lets Trino query Doris.
- [Trino DuckLake catalog](https://github.com/brikk/ducklake-integrations/tree/main/jvm/trino-ducklake)
  with read and write support.
- [Trino Duckbridge](https://github.com/brikk/duckbridge) gives Trino remote access to DuckDB.

## Feedback

Found a bug or SQL the plugin gets wrong?
[Open an issue](https://github.com/sort-dev/trino-intellij-plugin/issues). A small, anonymized SQL
sample helps a lot. Want to build or change the plugin? See
[dev-docs/CONTRIBUTING.md](dev-docs/CONTRIBUTING.md).

## License

Apache License 2.0, see [LICENSE](LICENSE). Copyright 2026 Sortdev SRL. Bundled third-party parts
are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

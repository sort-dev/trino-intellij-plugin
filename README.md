# SQL Dialect for Trino

A full featured **Trino SQL dialect** for DataGrip and IntelliJ-family IDEs — validated by **Trino's own
parser** (io.trino:trino-parser, bundled, currently version **483**), so editor errors and reporting
are correct.

Part of our SQL-tooling family alongside:

* [**Trino - Doris connector** allowing Trino to access your Doris data](https://github.com/brikk/trino-doris-connector)
* [**Trino native DuckLake Catalog** with full Read/Write support](https://github.com/brikk/ducklake-integrations/tree/main/jvm/trino-ducklake)
* [**Trino Duckbridge** - remote access to DuckDB via Quack](https://github.com/brikk/duckbridge)
* [**SQL Dialect Transpiler** for various datalake dialects and DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/32900-sql-transpiler)
* [**DuckDB SQL** (embedded+Quack) for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/33098-sql-dialect-for-duckdb-embedded--quack-)
* [**Doris SQL Dialect** for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/32777-sql-dialect-for-apache-doris)
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
  with no pg_catalog).
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

The next release is **0.4.1**, which upgrades the delivered `0.4.0-dev` builds.

```bash
./gradlew buildPlugin   # → build/distributions/trino-intellij-plugin-<dev-version>.zip
./gradlew buildPlugin -Prelease=true   # → build/distributions/trino-intellij-plugin-0.4.1.zip
./gradlew test          # boot + driver-facts + parser-authority (census scoreboard arrives with Stage 1)
```

Compile SDK: IDEA 2026.1.3, Kotlin 2.4.10. Plugin and helper classes target Java 21;
the bundled Trino 483 parser requires Java 25. Build/test with JDK 25 and use the
supported IDE's bundled JBR 25. Kotlin integration is optional in DataGrip.

`./gradlew test262 test263` runs the full offline suite on newer IDE runtimes using
the same 261-compiled classes. The generated-shape test runs on `test262` with
`-Ptest.ide262=/path/to/IDEA-262.9437.185` and
`-Ptest.brikkCompilerPlugin=/path/to/the/reviewed/compiler-plugin.jar`.

The shared catalog unconditionally selects released `dev.brikk.house` libraries from
Maven Central: `brikk-sql-jvm`, `brikk-sql-metadata-jvm`, `brikk-sql-verify-api` and
`brikk-sql-verify-trino` **0.20.0**; `brikk-sql-intellij-support` and test-only
`brikk-sql-intellij-test-support` **0.2.0**; `brikk-engine-kotlin-intellij-support`
**0.3.0**. There are no snapshot overrides or Maven Local substitutes. The ZIP embeds
the runtime helpers, never test-support, so installation requires no Maven setup.

Release prerequisites: the owner must publish SQL/core/metadata/verification and
support/test-support to Maven Central first, then the Kotlin helper. Formal compilation,
tests and packaging require those publications. `-Prelease=true` uses the base version
unchanged and makes `buildPlugin` run the shared `verifyRelease` dependency guard.
Release CI validates the ZIP filename, main JAR and plugin descriptor with the shared
`build-logic/tools/verify-release-zip.py` before uploading the same path without renaming it.
Property key/value completion remains disabled.

### Opt-in external SQL scans

```bash
./gradlew --no-daemon --max-workers=2 test test262 test263 -Ptest.kotlin=absent \
  -PrealSql.dir=/path/to/sql '-PrealSql.glob=*trino*.sql'
```

Both the directory and file glob are required; neither has a default. Only matching
SQL files are read, never modified. An explicitly approved additional file can be
included with `-PrealSql.extraFile=/path/to/one.sql`; it has no default. Every statement goes through registered Trino
highlighting with a DDL-derived model and with no model, with PIPE off and on.
There are no server connections; introspection is intercepted locally. Missing
metadata reports are recorded separately from syntax and column findings.

Raw statements, diagnostics, IDE logs/caches and scan test reports are confined to
`build/realsql/`. Treat that directory as private scratch and delete it after
triage. Only invented-name, neutral-literal replay tests belong in the repository;
public reports should contain shapes and counts, not source text or names.

## Built-in PIPE queries

Enable **Settings > Tools > Database > Trino PIPE** for the current project. No SQL Transpiler
installation is required. The workspace setting defaults off; `-Dtrino.pipes=false`
is an emergency veto. Changing it reparses open SQL files.

```sql
FROM tpch.tiny.orders
|> WHERE orderstatus = 'O'
|> AGGREGATE count(*) AS orders GROUP BY custkey
|> ORDER BY orders DESC
|> LIMIT 10
```

Normal Execute lowers PIPE to Trino SQL through the usual console. Selection,
script execution, parameters, result tabs and console ownership are preserved.
Every selected PIPE is validated before the first request. Unsupported/lossy
translation and invalid generated SQL stop execution.

Alt+Enter or the **Trino PIPE** context menu provides generated-SQL preview and
run-through-caret-stage. Stage keywords and known prior-stage columns complete.
Run-to-stage selects top-level stages; a caret inside a nested pipeline is refused
rather than silently running later inner stages. Normal Execute lowers nested pipelines.
The translator retains exact rendering/source-map pairs. Console server errors
currently remain visible against generated SQL; parameter-expanded coordinates
are not presented as exact authored-source locations.

## Brikk Kotlin editing

Resolved `Sql.trino` calls inside `@BrikkSql` functions automatically enable a
file-local editing profile in IDEA with Kotlin K2. The project PIPE setting stays
independent. For example:

```kotlin
@BrikkSql
fun selected(src: Rel<Events>, tenant: String) = Sql.trino("""
    FROM $src()
    |> WHERE tenant_id = $tenant
    |> SELECT id, tenant_id
""".trimIndent())
```

The SQL inspection view uses `src()` and `:tenant`, while Kotlin retains the
authored references for rename and navigation. Declared and compiler-generated
input contracts supply scoped column completion, resolution and Kotlin navigation.
Aliases, joins and projection visibility are statement-local. Partial inputs stay
partial; Kotlin type families do not invent exact SQL types.

Inspection templates cannot be executed or previewed as compiled output. Execute
the compiled/composed relation with its binding map. Complex interpolation is
reported on the authored Kotlin; pending analysis withdraws stale classifications.

Shared SQL/IDE mechanics live in `brikk-sql-intellij-support`. Kotlin recognition
and contracts come from `brikk-engine-kotlin-intellij-support`. See
[the historical verification report](REVIEW-trino-pipes-brikk.md) for earlier
integration checks.

## Credits

- **[Trino](https://trino.io)** — the query engine this plugin exists for, and the publisher of
  the parser it bundles. We are an independent project: not affiliated with, and not endorsed by,
  the Trino Software Foundation. "Trino" is their trademark, used only to identify the engine.
- Full third-party attributions (trino-parser, StarRocks-lineage parsing techniques):
  THIRD_PARTY_NOTICES.md.

## License

Apache-2.0. Independent community plugin by Sortdev SRL.

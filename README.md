# SQL Dialect for Trino

A real **Trino SQL dialect** for DataGrip and IntelliJ-family IDEs — validated by **Trino's own
parser** (io.trino:trino-parser, bundled, currently **483**), so editor errors are the engine's
errors.

**Status: early seed** (same-day sibling of our shipped
[SQL Dialect for Apache Doris](https://github.com/sort-dev/doris-intellij-plugin) and
[SQL Dialect for DuckDB](https://github.com/sort-dev/duckdb-intellij-plugin) plugins; the
measured-coverage machinery — census against Trino's own test suite, engine-exact validation,
function completion — is being built on that proven architecture). See PLAN.md.

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

# Contributing

Build and release notes for contributors. User documentation lives in the
[README](../README.md).

## Building from source

Release **0.5.0** upgrades `0.4.4` and development builds with Trino 484 support.

```bash
./gradlew buildPlugin   # → build/distributions/trino-intellij-plugin-<dev-version>.zip
./gradlew buildPlugin -Prelease=true   # → build/distributions/trino-intellij-plugin-0.5.0.zip
./gradlew test          # boot + driver-facts + parser-authority (census scoreboard arrives with Stage 1)
```

Compile SDK: IDEA 2026.1.3, Kotlin 2.4.10. Plugin and helper classes target Java 21;
the bundled Trino 484 parser requires Java 25. Build/test with JDK 25 and use the
supported IDE's bundled JBR 25. Kotlin integration is optional in DataGrip.

`./gradlew test262 test263` runs the full offline suite on newer IDE runtimes using
the same 261-compiled classes. The generated-shape test runs on `test262` with
`-Ptest.ide262=/path/to/IDEA-262.9437.185` and
`-Ptest.brikkCompilerPlugin=/path/to/the/reviewed/compiler-plugin.jar`.

## Dependencies

The plugin's `settings.gradle.kts` overrides the shared catalog with formal `dev.brikk.house` coordinates from
Maven Central: `brikk-sql-jvm`, `brikk-sql-metadata-jvm`, `brikk-sql-verify-api` and
`brikk-sql-verify-trino` **0.23.0**; `brikk-sql-intellij-support` and test-only
`brikk-sql-intellij-test-support` **0.4.0**, with the optional `brikk-sql-intellij-jdbc-support` **0.4.0**;
`brikk-engine-kotlin-intellij-support` **0.3.0**. There are no snapshot overrides or Maven Local
substitutes. The ZIP embeds the runtime helpers, never test-support, so installation requires no
Maven setup. Trino parser and JDBC are pinned to **484**. The vendored conventions remain unchanged.

`-Prelease=true` uses the base version unchanged and makes `buildPlugin` run the shared
`verifyRelease` dependency guard. Property key/value completion remains disabled.

For Marketplace publication of the same release version, run `./gradlew publishPlugin -Prelease=true`
from the release commit. This uploads to Marketplace; the GitHub release workflow does not.

## SQL coverage census

The coverage census regenerates mechanically per engine bump with `./gradlew harvestCensus`,
so coverage is re-proven per Trino version.

## Release artifacts

Commit release preparation to `main`, then create `release/<version>` from that commit.
The shared workflow rejects a commit absent from main, builds with `-Prelease=true`, and runs API
verification on IntelliJ 262 and 263. Only exact owner-confirmed internal usages in
`api-usage-approvals.txt` are accepted; new call sites block the GitHub release. Full reports are
retained. Compatible alone is not Marketplace approval. The owner handles Marketplace uploads.
GitHub release 0.4.1 was withdrawn; its tag and local ZIPs are historical audit evidence.

Manual dispatch on `release/<version>` runs the same guarded workflow. Use
`gh workflow run release.yml --ref release/<version>` if `[skip ci]` skipped the push trigger.

Normal CI keeps its main/froze-over, pull-request and manual triggers and existing tests.
Its manual verifier remains available; release branches run their own strict API verification.

## Opt-in external SQL scans

```bash
./gradlew --no-daemon --max-workers=2 test test262 test263 -Ptest.kotlin=absent \
  -PrealSql.dir=/path/to/sql '-PrealSql.glob=*trino*.sql'
```

Both the directory and file glob are required; neither has a default. Only matching
SQL files are read, never modified. An explicitly approved additional file can be
included with `-PrealSql.extraFile=/path/to/one.sql`; it has no default. Every statement goes
through registered Trino highlighting with a DDL-derived model and with no model, with PIPE off
and on. There are no server connections; introspection is intercepted locally. Missing
metadata reports are recorded separately from syntax and column findings.

Raw statements, diagnostics, IDE logs/caches and scan test reports are confined to
`build/realsql/`. Treat that directory as private scratch and delete it after
triage. Only invented-name, neutral-literal replay tests belong in the repository;
public reports should contain shapes and counts, not source text or names.

## Further dev docs

- [PLAN.md](PLAN.md) — the coverage plan
- [INTROSPECTION-PARITY.md](INTROSPECTION-PARITY.md) — source-vs-live introspection inventory
- [REPORT-truth-tree.md](REPORT-truth-tree.md) / [REPORT-truth-console.md](REPORT-truth-console.md) —
  truth-battery reports pinned by `src/test/kotlin/dev/sort/trino/probe/`
- [REVIEW-trino-pipes-brikk.md](REVIEW-trino-pipes-brikk.md) — PIPE / shared-support verification
- [brikk-kotlin-editing.md](brikk-kotlin-editing.md) — the `@BrikkSql` Kotlin editing profile
- [archive/](archive/) — historical cross-repo handoffs and replies

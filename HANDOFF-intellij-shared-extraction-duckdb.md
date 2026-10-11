# DuckDB handoff: shared SQL IntelliJ support

The 0.3.2 dogfood checkout and ZIP were left intact. DuckDB is the next proposed
consumer after Trino; perform this as a separate backport with its own artifact gates.

Published releases are `brikk-sql-intellij-support:0.1.0` and SQL core/metadata `0.19.0`,
all under `dev.brikk.house`. See [release evidence](REVIEW-trino-pipes-brikk.md).
Source: `/home/jayson/DEV/brikk/brikk-sql-worktrees/intellij-support`, initially
`d073725` and `a7af8fd`, with release notes in `b865a84`; all are on brikk-sql main.
The existing Kotlin helper remains a separate dependency.

## Backport order

1. Consume the SQL support library with Kotlin stdlib excluded. Replace action
   configuration, wrappers and promotion first. Use
   `CooperativeExecuteConfiguration`, `ExecuteInterceptor`, `ExecutionClaim` and
   `CooperativeExecutePromoter` in `dev.brikk.house.sql.intellij`.
2. Adapt checked DuckDB translation to `SqlExecutionAdapter` and replace the
   duplicated ScriptModel machinery with `TranslatingScriptModel`. Platform user
   parameters are snapshotted and normalized only for inspection discovery;
   actual values are substituted before final generation/validation. This supports
   configured parameters in literals and lowering-sensitive positions such as LIMIT.
3. Keep DuckDB's native-validator availability policy, datasource resolution,
   notifications and inspection-template guards in the product. A claimed failure
   cannot become raw SQL or predecessor execution.
4. Move lexical SQL work to core only after matching DuckDB's quote, nested-comment,
   dollar-string and parameter corpus against `SqlScriptSnapshot` and
   `parseParameterOccurrences`. Stage splitting is still `PipeStageSplitter`;
   complete statement ranges are a separate API.

The library has no SQL engine dependency, so action adoption need not force an
engine upgrade. The new core APIs require 0.19.0. No native JDBC/verifier or SDK
artifact is published transitively by the helper.

## Relation editing

Keep the current injection mechanics and copied Kotlin contracts. A universal SQL
resolver was deliberately deferred. Trino's adapter uses the same semantic cases
for aliases, joins, partial inventories, projection visibility and generated-column
navigation, with two details worth comparing:

- A named computed projection has a known column name even if its native type is
  unknown. Column inventory completeness and type knowledge are separate.
- Core 0.19 fixes the exact reserved `rows()` slot case. Remove inspection retagging
  only for cases proven supported by core/compiler tests, not all reserved names.

## Backport verification

Retain real recording-console tests, four variants, six registration orders,
detach behavior, mixed scripts, parameter cancellation/failures, stale snapshots,
Kotlin-absent loading, generated-shape tests and final distribution isolation.
Trino's isolated tests load your existing ZIP and exercise actual configuration
classes, not only fake peers. Cross-loader checks use platform interfaces rather
than casting to another plugin's private helper class.

On 263, fixture dialect mapping uses the public workspace setter and reparse.
DDL `sync()` computes synchronously and removes `waitComputed()`. On older SDKs,
the optional wait stays on the pooled thread to avoid the EDT deadlock.
Inspection fixtures use registered IDs such as `SqlResolveInspection`, because
implementation packages moved. Include caret-outside-statement whole-script
execution in the ownership tests; it must still lower selected pipelines.
Repeated console fixtures also use unique file URLs and datasource names, avoiding
old-console cleanup affecting the next fixture. Run-to-stage refuses a caret
inside a nested pipeline instead of silently executing later nested stages.

The shared model preserves source-map identity, but cannot prove authored offsets
after arbitrary parameter expansion. Keep unavailable map-back explicit. The
source-preserving renderer also remains unmapped.

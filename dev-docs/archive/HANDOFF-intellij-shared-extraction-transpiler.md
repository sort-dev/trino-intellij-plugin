# SQL Transpiler handoff: shared extraction

Your current bug cleanup remains independent. No Transpiler source, build or
installed artifact was changed by the Trino work.

Published on Central: `dev.brikk.house:brikk-sql-intellij-support:0.1.0` and matching
SQL core/metadata `0.19.0`. See [the release report](../REVIEW-trino-pipes-brikk.md)
for actual publication status and verification. Source worktree:
`/home/jayson/DEV/brikk/brikk-sql-worktrees/intellij-support`.

The source is also on brikk-sql main. **Verify and oracle are now available as
0.19.0**, following the initial scoped core/helper release. Oracle exports verify
0.19.0. [Release evidence](https://github.com/brikk/brikk-sql/actions/runs/38107790876).
The SQL IntelliJ helper remains independently versioned at 0.1.0. Retain explicit
core/verifier version choices while completing your native-verification review.

## APIs useful to your plan

Column-display follow-up: SQL IntelliJ support 0.1.1 adds `SqlColumnCompletion`
for alias/type/nullability/origin rendering. The current Transpiler source and
descriptor have no column-completion contributor, so this change needs no local
adoption. It is available if that feature is added later; the rendering consumers
today are the dialect plugins.

- **Core script ranges:** `SqlScriptSnapshot.parse(text, dialect)` returns ranges
  with leading trivia/terminator ownership, half-open UTF-16 offsets, actual PIPE
  visibility and explicit incomplete/unsupported boundary errors. Commands retain
  visible operator tokens in editor mode; normal parser command folding is unchanged.
  `PipeStageSplitter` remains a separate stage-only API.
- **Core parameter discovery:** `parseParameterOccurrences(sql, dialect)` is now
  parse-only. It returns original full/name spans and refuses unproved input.
  IDE-configured textual user parameters remain a platform concern.
- **Document snapshots:** `EditorSnapshot.capture(document)` and `requireCurrent()`
  protect exact document/revision identity, including edit-and-revert.
- **Cancellation:** `recoverEditorOperation` propagates platform/coroutine
  cancellation and JVM errors; only ordinary exceptions become recovery results.
- **Execution preparation:** `TranslatingScriptModel` and `SqlExecutionAdapter`
  preserve source ranges, platform parameters and selected-script request chaining.
  Finalize all translated statements after prompting and before the first request.
  Generation/verification/approval policy belongs to your adapter.

Your explicit actions need not register global Execute wrappers to use the other
utilities. The library registers nothing by itself and has no engine, native
verifier, Kotlin IDE or compiler dependency. Exclude its Kotlin stdlib dependency
when embedding it in an IDE plugin.

## Important boundaries from your feedback

ST-X1 through ST-X8 were incorporated. In particular:

- No existing defective Transpiler splitter, marker scanner, raw-fallback path or
  approval policy was extracted. Their failures remain regression requirements.
- Keep a certification report's exact result with its generated SQL. Source-preserving
  rendering has no source map. Parameter-expanded input is a new coordinate space;
  the shared model exposes an authored map only for unchanged input.
- Core findings retain their existing meanings. `TranspileReport.ok` is not an
  automatic execution policy; unavailable/advisory verification stays distinct.
- Native verification and driver/parser discovery remain product responsibilities.
  This extraction does not resolve your safe-native-verification work.
- The core range API handles Trino routine bodies and tested lexical forms, not
  arbitrary procedural languages. Unsupported delimiter/procedure/trigger boundaries
  are opaque refusals, not permission to execute a guessed prefix.

Trino validates the peer-owned Kotlin helper `0.2.0-SNAPSHOT`, including genuine
generated shapes. Continue using supported optional host recognition for your
Replace/Insert/Send/Execute guards rather than Trino/Doris/DuckDB private keys.
The helper's Central promotion is separate from this SQL-support release.

Package and classloader tests must distinguish helper Java-21 bytecode from your
intentional Java-25 Trino-parser dependency. Trino's fixtures also demonstrate
per-project verifier caches, avoiding extracted-plugin cache races between agents.

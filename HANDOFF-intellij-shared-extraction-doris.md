# Doris handoff: shared SQL IntelliJ support

Owner of extraction: Trino/Astra. Doris's dogfood checkout and ZIP were left intact.
This is a separate backport, not an instruction to replace the user's installed build.

## Artifacts and source

Published on Maven Central: `dev.brikk.house:brikk-sql-intellij-support:0.1.0` and
core/metadata `0.19.0`. Release verification/status is recorded in
[the Trino report](REVIEW-trino-pipes-brikk.md).

Source worktree: `/home/jayson/DEV/brikk/brikk-sql-worktrees/intellij-support`.
Commits: `d073725`, `a7af8fd`, release notes `b865a84`, now also on brikk-sql main.
The support module is in **brikk-sql**,
separate from the peer-owned Kotlin helper. It has no engine, verifier or Kotlin IDE
dependency; its only published dependency is Kotlin stdlib, which consumers exclude.

## Suggested backport

1. Add the support dependency with Kotlin stdlib excluded. Start with actions only;
   that does not require a simultaneous upgrade of Doris's engine or Kotlin helper.
2. Replace the duplicated configuration/wrappers/promoter with consumer subclasses
   of `CooperativeExecuteConfiguration` and `CooperativeExecutePromoter` from
   `dev.brikk.house.sql.intellij`. The interceptor receives the original event and
   live `ExecOption`. Return `DECLINED` only for unowned input; rejection is `HANDLED`.
3. Preserve the public dynamic-customizer registration and current project gates.
   Settings changes do not re-register actions. Detach/restore is implemented by
   the library, including retained inner wrappers that can require restart.
4. Adopt `TranslatingScriptModel` after adapting Doris's checked translator and
   parameter ranges to `SqlExecutionAdapter`. Keep native Doris validation and
   server-error auditing in Doris. Run parameter prompting first, finalize every
   selected translation, then use the normal console request chain.
5. Preserve map provenance. `RenderedSql` pairs exact SQL/input/map; `authoredMap`
   is unavailable after parameter expansion changes the input. Do not reinstate an
   old source map or exact-looking heuristic when adapting server diagnostics.

Core 0.19 adds `SqlScriptSnapshot.parse` and `parseParameterOccurrences`. The first
retains trivia and half-open UTF-16 boundaries with explicit incomplete/unsupported
outcomes. The second discovers parser-proved ranges without lowering. Compare the
Doris lexer/scanner corpus before replacing it. Client delimiter directives and
unsupported procedural forms deliberately return uncertain boundaries.

Core also accepts the exact zero-argument `rows()` table-slot form without altering
window-frame ROWS syntax. This is not a claim that all keyword-named slots work.

## Preserve during migration

- Native validation, catalog refresh, settings, notifications, diagnostics and
  server position conversion remain Doris policies.
- Continue binding only slots used by the current fragment. Keep partial/unknown
  contracts conservative and invalidate on Kotlin trait edits without SQL edits.
- The library uses platform `ActionWithDelegate` across loaders. Update tests that
  identify Trino wrappers solely by a `dev.sort.trino.pipes` class-name prefix;
  Trino's wrapper now belongs to its own copy of the shared library.
- Keep existing execution, settings, safety, action, source-map and relation tests.
  Trino exercised all six orders using actual Doris/DuckDB/Trino configuration
  classes in isolated product loaders; preserve that gate in the backport.

Verification fixture lessons: 263 needs the public synchronous workspace dialect
setter plus reparse. Its DDL `sync()` is synchronous and `waitComputed()` is gone;
use the optional older wait only where present, on a pooled thread.

Resolve inspection fixtures by their registered short names, including the
`Inspection` suffix, rather than linking their old implementation package.
Trino also covers caret-outside-statement execution settings; include that case
when adapting the ownership precheck so stock whole-script execution cannot
bypass lowering merely because the caret is in a trailing comment.
Use unique virtual-file URLs and datasource names in repeated recording fixtures;
otherwise cleanup of one console can invalidate the next fixture's console lookup.
Trino's run-to-stage explicitly refuses a caret inside a nested pipeline and
supports initial FROM/SELECT prefixes only after claiming the enclosing pipeline.

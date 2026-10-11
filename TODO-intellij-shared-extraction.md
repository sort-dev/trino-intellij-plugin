# Shared IntelliJ support extraction

Updated 2026-10-10. Owner: the Trino/Astra agent.

Status: P0-P7 complete. Shared library published, core changes integrated on main,
Trino 0.3.0 committed and its final ZIP verified. Kotlin helper 0.2.0-SNAPSHOT remains
the user-requested development dependency; its Central promotion and peer backports
are the explicit follow-ups below. Consumer implementation commit: `7c9b968`.

## Goal and working state

Deliver built-in Trino PIPE support and Brikk Kotlin editing, using a new embedded
library, `dev.brikk.house:brikk-sql-intellij-support`, in the **brikk-sql repository**.
Trino is the first consumer. DuckDB and Doris backports follow as separate tasks.

| Item | Location or baseline |
| --- | --- |
| Trino consumer | `/home/jayson/DEV/sortdev/trino-intellij-plugin` |
| Library worktree | `/home/jayson/DEV/brikk/brikk-sql-worktrees/intellij-support` |
| Library branch | `intellij-support` |
| Updated SQL baseline | `3fa69f2`, shared by `origin/main` and `origin/release/0.18.1` at fetch time |
| Released SQL dependencies | Core and matching metadata `0.19.0` |
| Repository development version | `0.19.0-SNAPSHOT`; source builds must retain honest development coordinates |
| Released support version | Independent `0.1.0`, available on Maven Central |
| Kotlin helper | Peer-owned `brikk-engine-kotlin-intellij-support:0.2.0-SNAPSHOT`, Maven Local during development |
| Reference implementations | Doris `178918b`, DuckDB `60fb207`; current dogfood artifacts stay under their owners' control |

### Completed preparation

- [x] **P0.1** Read [Transpiler feedback](REPLY-intellij-shared-extraction-transpiler.md),
  [DuckDB feedback](REPLY-intellij-shared-extraction-duckdb.md) and
  [Doris feedback](REPLY-intellij-shared-extraction-doris.md).
- [x] **P0.2** Fetch origin and fast-forward both SQL main and the clean
  `intellij-support` worktree from `4db2589` to `3fa69f2`. Confirm main and the
  0.18.1 release branch identify the same commit.
- [x] **P0.3** Replace the preliminary `.sql` checklist with this Markdown tracker.

## Decisions after review

1. **Trino first.** Build and verify the new API through Trino. Before the first
   public release, exercise a DuckDB-shaped second-consumer adapter in an isolated
   compile/contract fixture. A full dogfood-plugin migration is unnecessary for
   that check. Their existing ZIPs are not build outputs for this task.
2. **Action mechanics first.** Extract registration, wrappers and promotion before
   sharing execution preparation. Consumers can use these independently.
3. **SQL semantics stay in core.** Add the missing script-range and parse-only
   parameter-range APIs there. Keep core independent of IntelliJ and reuse its
   tokenizer/parser rather than promoting consumer scanners into a second engine.
4. **Keep product policy explicit.** The library supplies mechanics and state;
   dialect ownership, enablement, native validation, reporting and approval remain
   adapters. Transpiler's reviewed cross-dialect policy is not automatic PIPE policy.
5. **Use Transpiler as evidence, not a correctness template.** Its owners are
   fixing cancellation, execution, slicing, markers and edit bugs. Reuse core
   findings/results and import regression requirements, not defective methods.
6. **Defer a universal resolver.** Reuse core scope/shape/lineage APIs and establish
   common relation cases first. Trino still gets its own required editing adapter;
   broader resolver extraction is a later decision backed by consumer evidence.
7. **Independent embedded libraries.** Each plugin owns its helper and engine
   classes. Cross-plugin cooperation uses platform interfaces, never private class
   casts, peer keys or a new global dispatcher. Retained wrappers may require restart.
8. **Independent versioning and scoped publication.** The SQL support module has
   its own version. New core APIs require an explicitly versioned core release;
   no development build is labelled as published 0.18.1.

### Feedback disposition

| Feedback | Decision and work items |
| --- | --- |
| ST-X1; DuckDB §1; Doris §1 | Accept the two concrete core API gaps, P2. Stage splitting and scalar parameter inventories do not satisfy them. |
| ST-X2; DuckDB §2; Doris §2 | Extract proven action behavior first, P1; share preparation only after P2, under P3. Preserve regression assertions. |
| ST-X3; DuckDB §3; Doris §3 | Accept explicit claim, validation, snapshot and map-provenance states, P3. |
| ST-X4; DuckDB §4; Doris §4 | Keep optional Kotlin semantics peer-owned; guard every execution/export entry point, P5. |
| ST-X5 | Native verification and driver/parser discovery remain product responsibilities, P3/P4. |
| ST-X6; peer verification requirements | Accept actual-runtime, negative-execution and real-classloader gates, P6. |
| ST-X7; peer publication recommendations | Accept independent 0.1.x support version, second-consumer fixture and scoped release, P6/P7. |
| ST-X8/H1-H6 | H1 in core, H2/H3 in SQL IDE support, H4 in test/build support, H5 contracts first, H6 reuses core results with product policy. |

## Implementation tracker

Order: P1, P2, P3, P4, P5, P6, P7. Compatibility discoveries feed back into
the affected step. P8 is a subsequent consumer migration, not a release prerequisite.

### P1. Compatibility baseline and cooperative actions

- [x] **P1.1** Establish Trino's existing test/package baseline. Adopt platform
  Gradle plugin 2.19.0 and the verifier targets below. Compile production code
  against IDEA 261 for optional Kotlin APIs, with Java 21 helper/product bytecode.
- [x] **P1.2** Resolve the actual Trino runtime requirement. Its parser 483 has
  Java 25 bytecode while CI requests JDK 21. Load and run it on each intended IDE
  runtime, align CI workers, and document the distribution's real requirement.
- [x] **P1.3** Replace Trino's removed `tryPerform` path with the tested
  project-scoped coroutine/`tryPerformSync` approach, retaining targeted refresh.
- [x] **P1.4** Create the JVM support module in brikk-sql. Action/snapshot APIs
  use platform types and callbacks without a core dependency where unnecessary.
  Declare any later core dependency honestly if public APIs expose core types.
- [x] **P1.5** Extract public `DynamicActionConfigurationCustomizer` registration,
  synchronous stub resolution, four predecessor captures and conditional restore.
  Detach wrappers retained beneath peers without replacing a peer's current action.
- [x] **P1.6** Preserve `RunQueryAction` classification, `ActionWithDelegate`, live
  variants, fixed Selection defaults, presentation, shortcuts, update thread,
  injected-context and document-commit policy. Centralize Selection defaults.
- [x] **P1.7** Extract cycle-safe promotion through the registered public
  `ActionPromoter` interface. Traverse foreign wrappers only via platform contracts.
- [x] **P1.8** Port meaningful chain/order/unregistration tests and establish a
  recording-console fixture outside the runtime artifact. Verify original-event,
  exactly-once delegation for unclaimed requests and all four action variants.

### P2. Missing SQL-domain APIs in core

- [x] **P2.1** Recheck 0.18.1 source and published API availability before adding
  APIs. Record the minimum core version required by each resulting operation.
- [x] **P2.2** Add a source-dialect-aware complete-script snapshot API with
  half-open UTF-16 statement ranges, explicit trivia/terminator ownership, real
  PIPE detection and incomplete/unsupported boundary outcomes. Preserve uncertain
  remainders; never execute a guessed prefix of an unterminated construct.
- [x] **P2.3** Test Trino compound/routine bodies, nested PIPE, long statements,
  quoted markers, comments, dollar quotes, Unicode and recovery. Use Doris/DuckDB
  scanner corpora as requirements, not universal scanner implementations.
- [x] **P2.4** Expose parser-proved parameter occurrence/full-name ranges through
  a narrow parse-only API. Today the exact extractor is private and its public
  results require source-preserving rendering; `scalarParams` gives ordinals.
  Define an explicit unavailable result for input whose parameter roles are unproved.
- [x] **P2.5** Test repeated binds and ambiguous colons in aliases, maps/structs,
  slices/casts, strings/comments and dollar parameters. Core SQL parameters remain
  distinct from configurable IDE user parameters, including substitutions in strings.
- [x] **P2.6** Keep nested-query selection explicit for stage operations.
  `PipeStageSplitter` gives token extents with an inclusive end; it is not a script
  splitter. `isPipe` is root-only. Normalize coordinate conventions at API boundaries.

### P3. Shared snapshots and execution preparation

- [x] **P3.1** Define small adapter contracts preserving these distinctions:
  unclaimed versus claimed/handled; generated versus executable; accepted,
  rejected and unavailable validation; advisory versus authoritative checks.
  Reuse core results/findings instead of duplicating their semantic taxonomy.
- [x] **P3.2** Set an explicit claim/preparation transition. Once execution is
  claimed, cancellation, rejection, missing ownership/client or preparation with
  no Info cannot trigger raw/predecessor fallback. Unexpected and fatal failures
  propagate. Only unclaimed input delegates.
- [x] **P3.3** Retain original event, live `ExecOption`, console, editor and
  platform-selected scope. Support FILE_EDITOR-only structure-view invocation.
  Keep per-invocation state out of globally registered actions.
- [x] **P3.4** Share `ScriptModel` translation and parameter snapshots using
  platform prompting/substitution plus P2's SQL ranges. After substitution, finalize
  and validate every selected PIPE before any ordinary or translated request.
- [x] **P3.5** Snapshot text/revision, ranges, dialects and destination. Recheck
  after prompting/asynchronous work and before apply/submission. Preserve stock
  request chaining, cancel behavior, editor coupling, new tabs and console ownership.
- [x] **P3.6** Tie maps to the exact returned SQL and input snapshot. Parameter
  expansion can change the input coordinate space: compose a proven map back to
  authored text or report mapping unavailable. Never label substituted offsets exact.
- [x] **P3.7** Keep source-preserving rendering explicitly unmapped. Do not attach
  a map from certification, preview or another generator invocation to that output.
- [x] **P3.8** Keep native validation in product callbacks. The helper discovers
  no peer parser JARs/drivers and changes no global verifier properties. Native
  parser acceptance remains distinct from semantic preservation and lowering refusals.

### P4. Built-in Trino PIPE consumer

- [x] **P4.1** Embed released core/metadata 0.19.0, which supplies the APIs in P2.
  Add default-off workspace settings, emergency
  veto, reparse/daemon refresh and independently owned helper/engine dependencies.
- [x] **P4.2** Integrate shared ranges into parser, execution and diagnostics while
  preserving Trino routine boundaries, lexer masks and parameter-reference PSI.
- [x] **P4.3** Lower top-level and nested PIPE. Reject unsupported/lossy output,
  raw passthrough and residual PIPE operators. Validate generated SQL using Trino's
  bundled parser before submission, including the post-substitution pass.
- [x] **P4.4** Add preview, run-to-stage, stage keyword coloring/completion and
  known stage-input columns. Marker-free initial stages are eligible only within
  an explicitly claimed pipeline. Preserve ordinary SQL and project-off behavior.
  The initial FROM or SELECT stage is supported; a caret inside a nested pipeline
  is refused explicitly rather than running later inner stages accidentally.
- [x] **P4.5** Scope diagnostic suppression to exact owned statements and retain
  native/server diagnostics. Verify Trino server column units before exact map-back;
  present unmappable errors without an invented authored-source squiggle.

### P5. Brikk Kotlin editing and relation contracts

- [x] **P5.1** Consume the peer-owned 0.2.0-SNAPSHOT helper with Maven Local scoped
  to its development coordinate. Record checksum/API baseline. Hot recognition uses
  `includeShapes = false`; Kotlin-aware entry points use an optional descriptor.
- [x] **P5.2** Implement Trino injection, trimming/source mapping, scalar binds,
  relation slots, supported constants, rename/navigation and pending-context retry.
  Reuse helper semantics and fresh copied contracts; never infer pending symbols.
- [x] **P5.3** Guard Execute, selection, run-to-stage and executable preview/export
  paths before console preparation. A Brikk inspection view is not a runtime
  relation. Pending recognition grants no permission to execute placeholders.
- [x] **P5.4** Resolve only slots used by the current fragment from fresh copied
  contracts. Trino uses the consumer scope adapter for partial contracts rather
  than closing them into `ShapeCatalog`; analysis is fresh, without a global slot cache.
  Preserve distinct occurrences and refresh after trait edits without SQL edits.
- [x] **P5.5** Preserve full/partial/unavailable/unsupported knowledge, alias
  hiding, CTE/subquery boundaries, ambiguity, nullable joins and input-to-stage
  visibility. A partial known-column list is not a closed full `Shape`.
- [x] **P5.6** Keep exact native SQL types, quoted identity and ordinal order
  unknown where Kotlin does not prove them. Use stable statement-occurrence-aware
  symbols; label generated-column navigation fallbacks. Keep physical-table
  resolution and inspections with the IDE.
- [x] **P5.7** Reproduce reserved called-slot cases such as `rows()` against the
  updated core; fix SQL-domain defects there rather than sharing editor retagging.
- [x] **P5.8** Add common relation regression cases and genuine generated-shape
  tests using the matching reviewed compiler artifact/runtime. Keep wider resolver
  extraction deferred until core and native-PSI approaches agree on those cases.

### P6. Consumer and artifact verification

- [x] **P6.1** Run all existing Trino regressions and recording-console cases for
  mixed scripts, parameters, current-document execution, owner/range identity,
  later-statement failures, missing clients, cancellation and submission failures.
  Destination identity is rechecked after preparation; document revision has an
  edit-and-revert regression. Caret-outside-statement whole-script scope is covered.
  Assert zero requests for every rejected/cancelled selected-script preflight.
- [x] **P6.2** Exercise all six three-dialect registration orders, four variants,
  disabled projects, absent selections, cycles and detach above/below peers.
  Include actual distribution wrappers/classloaders as well as contract doubles.
- [x] **P6.3** Test standalone, SQL Transpiler absent/present and peer coexistence.
  Assert engine/helper ownership and different-loader/version wrapper cooperation.
  Transpiler 0.3.0 cannot load on 263; use an eligible candidate when available or
  record that lane blocked, without modifying its released descriptor.
- [x] **P6.4** Run optional Kotlin-present/absent isolation and genuine generated
  shape lanes. Do not count excluded cases as passes or block DDL fixture waits
  on the EDT while introspection needs the same lock.
- [x] **P6.5** Run the same 261-compiled/instrumented artifact on newer runtimes;
  verify the final ZIP against all five targets below. Report API-use notices and
  keep binary, dependency, structural, override-only/non-extendable violations fatal.
- [x] **P6.6** Run an isolated DuckDB-shaped second-consumer compile/contract
  fixture before freezing the first public API. Keep dogfood repos and ZIPs intact.
- [x] **P6.7** Check representative generated queries and cancellation against
  live Trino when available; record this separately from offline/runtime/verifier results.
- [x] **P6.8** Audit exact ZIP contents, notices, optional descriptors and runtime
  dependencies. Check Java 21 helper/product bytecode separately from intentional
  Java 25 parser artifacts; verify the parser actually executes on supported runtimes.

Verifier targets: `DB-261.24374.56`, `IU-262.8665.81`, `DB-262.10315.132`,
`IU-263.4732.28`, `IU-263.5701.42`. Trino's advertised range remains 261/262
until a separate range decision is justified by runtime results.

### P7. Module-scoped release and final consumer

- [x] **P7.1** Add isolated support-module publishing with independent versioning.
  Do not add it to an all-modules release under the support version. Record and
  release any required additive core API version before the final consumer combination.
- [x] **P7.2** Inspect JAR, sources, licenses/notices, POM and Gradle metadata.
  Publish no local SDK coordinates or IDE/compiler, JDBC, native verifier or test
  infrastructure as runtime dependencies. Compile SDK inputs are provided only.
- [x] **P7.3** Verify a fresh Maven consumer and the Trino combination before
  publishing. Pin all tested artifact identities and required core/IDE versions.
- [x] **P7.4** Publish scoped SQL release artifacts to Central and consume them
  without Maven Local shadowing. Direct downloads, checksums, class payloads and
  consumer checks pass. Kotlin's explicitly requested snapshot is the sole local exception.
- [x] **P7.5** Record final plugin ZIP hash, commits, commands and pass/skip/blocked
  results. Release gates attach to that artifact, not an earlier snapshot or version.

### P8. Subsequent backports

- [x] **P8.0** Write separate Doris, DuckDB and Transpiler handoff files with APIs,
  version boundaries, fixture compatibility lessons and verification requirements.
- [ ] **K1** Peer-owned follow-up: promote the Kotlin helper 0.2.0-SNAPSHOT to a
  released version, switch Trino's remaining dependency and repeat consumer checks.
  This is the requested local-snapshot arrangement, not a hidden Central dependency.

- [ ] **P8.1** Port DuckDB in a separately scoped change with its owner after Trino
  passes. Preserve its dogfood behavior and repeat console, Kotlin and artifact gates.
- [ ] **P8.2** Port Doris with equivalent action, settings, diagnostics, map-back,
  parameter, relation and classloader regression coverage.
- [ ] **P8.3** Transpiler adopts the independently usable document/snapshot/diagnostic
  utilities described in its handoff. Its bug fixes proceed independently of this extraction;
  it need not register global Execute wrappers to consume the library.

## Progress evidence

Final 2026-10-10 evidence is recorded in [the verification report](REVIEW-trino-pipes-brikk.md):

- Original Trino baseline: `./gradlew test`, successful on Java 25.
- Final consumer: 132 tests, 110 passed, 22 live/optional skips. Recording-console,
  action/promotion, injection, physical-DDL and relation-scope regressions pass.
- Core: `./kotlin test -m brikk-sql`, 852 tests passed, no skips/failures. New script,
  parameter-range, routine-condition and reserved-slot tests are included.
- Shared support 0.1.0 and core/metadata 0.19.0 are on Central. Direct downloads,
  checksums, sources/notices, metadata and class-payload comparisons passed.
- First five-target verifier attempt was interrupted by a shared verifier-cache
  extraction race, not a reported binary verdict. Trino now owns an isolated cache.
- The compiled 261 IDEA SDK ships JBR 25.0.3. Product/core bytecode remains Java 21;
  Trino parser 483 intentionally requires Java 25. CI now requests Java 25.

- Final runtime checks: DataGrip 262 without Kotlin, IDEA 263 with the same 261
  classes, genuine compiler-generated shapes, and actual three-plugin registrations
  in all six orders pass. Released SQL Transpiler's 263 range remains a documented
  coexistence limitation; no descriptor was modified to bypass it.
- Live Trino 483 query equivalence, cancellation and targeted introspection pass.
- The ZIP passes package audit and the five-target verifier matrix; its SHA-256 is
  `b09fae308046c6b2f2af5dffd268bfabbaefc56c28c0767670fb7ceb19aca100`.
- Source commits: library `d073725`, `a7af8fd`, notes/main integration `b865a84`;
  Trino implementation `7c9b968`.
- Three handoff files accompany this report. Doris and DuckDB dogfood artifacts
  were not rebuilt or modified; their migrations remain separate work.

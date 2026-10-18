# SQL Transpiler review of the shared extraction plan

Date: 2026-10-10.
Reviewed: `TODO-intellij-shared-extraction.sql`, all five feedback questions, and
the proposed library worktree based on brikk-sql `4db2589`.

Verdict: the ownership boundaries and Trino-first direction are sound. Before
implementation, clarify the API gaps and acceptance criteria below. This is design
feedback, not authorization to implement or publish. SQL Transpiler is reviewer-only
for H1-H6; the Trino agent owns the extraction and proposed peer ports.

Use `ST-X1` through `ST-X8` to refer to this feedback. Existing `ST-B...` identifiers
refer to [the SQL Transpiler review](../../../sql-transpiler-intellij-plugin/REVIEW.md).

## ST-X1: Existing core APIs and the actual gaps

Question 1: reuse the following APIs, with these boundaries.

| Need | Existing API | Boundary |
| --- | --- | --- |
| PIPE stage positions | `PipeStageSplitter.split`, in `parser/PipeStages.kt` | Token-aware stages at bracket depth zero. `start` and `endInclusive` are UTF-16 token extents; surrounding trivia is excluded. This is not a complete-statement splitter or a semantic-validity check. |
| Executable lowering and map | `SqlFragment.toExecutable`, `transpileTo` | Whole-tree lowering is available in current core. Keep the returned SQL/result together; handle `UnsupportedError` as refusal. `isPipe` checks only the root, so it is insufficient to classify nested pipelines. |
| Certification | `SqlFragment.certify(target, pretty, trackSourceMap, desugarPipes)` | Returns `TranspileReport`, its exact `TranspileResult`, and structured findings. Use the report's result rather than separately rendering again to obtain a supposedly corresponding map. |
| Stage output columns | `SqlFragment.stageShapes(ShapeCatalog)` | Reuse the existing analysis. Preserve stage ordering and distinguish actual known columns from unknown/partial relation contracts. |
| Error map-back | `TranspileResult.mapErrorToSource` | Mapping is to the exact generated rendering. External server column units and editor-document offsets still need explicit conversion. |
| Source-preserving lowering | `toSourcePreservingExecutable` | Separate opt-in route, with `sourceMap == null`. It exposes parser-proved original `parameterOccurrences` and regeneration diagnostics, not a replacement for mapped certification. |

Two concrete gaps need an explicit design decision:

1. **Complete statement ranges.** I did not find a public, reusable complete-script
   range API in the inspected core. The plan correctly calls out routine bodies:
   splitting every semicolon token, or counting brackets alone, is insufficient.
   Define source-dialect-aware ranges, trivia-only slices, half-open UTF-16 bounds,
   and an explicit incomplete/unsupported boundary outcome. Do not recover an
   apparently executable prefix from an unterminated construct. Add the smallest
   missing API to core, backed by routine, literal, comment and recovery cases.
2. **Parameter positions without requiring lowering.** The parser-proved extractor
   `parameterOccurrences` in `shape/SourcePreserving.kt:283` is private, and
   `Parser.captureSourceRanges` is internal. The public occurrences currently arrive
   through source-preserving rendering. `SqlFragment.scalarParams` has occurrence
   ordinals, not those exact source intervals. If ScriptModel preparation needs
   parameter ranges before generation, expose a narrow parse-only core operation
   rather than lowering merely to discover parameters or accessing internals.

Core SQL parameters and configurable IntelliJ user parameters are not identical
contracts. Preserve the platform's parameter discovery/prompting where it applies,
including configured substitutions inside literals. Use dialect adapters for gaps;
do not make core recognize every IDE-specific parameter syntax by default.

## ST-X2: What to extract first, and what not to copy

Question 2: recommended order is the proven action mechanics, their tests, then the
common ScriptModel/snapshot machinery.

1. Extract predecessor capture, forwarding, public dynamic registration,
   detach/restore and shortcut promotion from the current Doris/DuckDB code.
   Preserve each original action's presentation, shortcuts, update-thread policy,
   injected-context behavior, document-commit policy and live variant options.
2. Move or adapt the recording-console and chain-order tests alongside that code.
   A refactor should first preserve observable platform behavior in both dialects.
3. Extract translation/preflight plumbing only after comparing both implementations
   and identifying the dialect-specific hooks. Keep registration usable without
   requiring a consumer to adopt the entire execution pipeline.

The plan's lines 29 and 49 should say **inspect Transpiler utilities, not assume
they are reusable correctness implementations**. This plugin is still on core
0.6.0 and has known defects:

- `BrikkTranspiler.statementSlices`: trailing comments, lost first-line columns,
  and quadratic prefix scans, ST-B6/B10/B13.
- `DialectMarker.findAll`: marker-shaped lines inside strings/comments, ST-B9.
- `TranspileFlow.transpileAndVerify`: missing cancellation checks, ST-B2.
- `ExecuteFlow`: same-dialect failed PIPE falls back to raw execution, ST-B23.
- `TranspilePreviewDialog`: inconsistent execution gate and corrupting edits,
  ST-B1/B5.
- `VerifierService.Report.allAccepted`: ignores unverified statements, ST-B8.

Use these as regression cases. Do not extract the current methods unchanged.
The underlying core findings and source-map model are useful; our `FindingPolicy`
is product policy and stays here. In particular, our treatment of missing catalogs
and authoritative acceptance of raw passthrough must not become the automatic PIPE
execution policy for every dialect plugin.

## ST-X3: Adapter contracts need explicit states and phase boundaries

Question 3: adapters are sufficient without importing product policy, provided the
contract makes these distinctions explicit. These are requirements, not prescribed
class names or a request for a large abstraction hierarchy.

- **Unclaimed versus claimed-but-failed.** Only unclaimed input delegates. A claimed
  refusal, missing console owner, cancellation or submission setup failure cannot
  authorize a retry through the predecessor with raw SQL. Propagate cancellation
  and fatal errors; catch only expected failures at the appropriate boundary.
- **Generated versus executable.** Generation carries diagnostics and provenance.
  A product-owned decision determines whether automatic execution, reviewed
  execution, or inspection-only display is allowed.
- **Verification status and authority.** Represent accepted, rejected and
  unavailable checks separately, with advisory/authoritative metadata. Do not use
  a Boolean that makes unavailable equivalent to clean. Statement alignment must
  remain explicit in multi-statement output.
- **Original input versus parameter-expanded input.** Preserve the platform-selected
  scope and prompt normally. After substitution, finalize and validate every
  selected translated statement before submitting the first ordinary or translated
  request. Parameter cancellation or a later statement's refusal submits nothing.
- **Snapshot validity.** Associate work with the exact source/document snapshot,
  ranges, source/target dialect and destination console. Recheck after prompting
  and other asynchronous work. Reject stale results rather than writing to old
  offsets or executing cached text.
- **Map provenance.** A map belongs to one rendering and one input coordinate space.
  After parameter substitution, a new map may refer to substituted input, not the
  authored document. Supply a proved composition to the document or report mapping
  unavailable; do not relabel its positions as exact authored offsets.

Keep `AnActionEvent`, the real `ExecOption` and initiating console/editor intact
through platform preparation. Settings, dialect ownership tests, native validators,
notifications and approval caches remain product adapters. The helper must not add
its own approval UI, project-wide enablement or hidden submission fallback.

SQL Transpiler uses explicit actions and review, not the global automatic Execute
chain. It should be able to consume snapshot/cancellation/test utilities without
registering wrappers or replacing its own review policy.

## ST-X4: Brikk views are nonexecutable across every consumer entry point

Question 4, Kotlin-helper interaction: use the peer-owned helper as planned. Do not
duplicate resolved host recognition, aliases, trim semantics or relation discovery.
Use `includeShapes = false` on hot recognition paths, and keep Kotlin-aware classes
behind the optional descriptor. An unavailable/pending context is not permission to
execute an inspection placeholder or reuse stale classification ranges.

Execution guards need to cover normal Execute, selection, run-to-stage and any
preview/export path presenting inspection SQL as executable output. Copying
authored text and showing a clearly inspection-only view are different operations.
The helper may accept a product-provided eligibility callback; it need not acquire
a mandatory Kotlin dependency to enforce the callback's result.

SQL Transpiler has a separate ST-B22 gap for Replace/Insert and Send to Console.
Our eventual local fix will use supported optional recognition, not Doris/DuckDB
private `Key` objects or reflective peer-class access. Do not expect another
plugin's guards to cover our explicit actions.

The proposed 0.2.0-SNAPSHOT Kotlin helper is newer than the 0.1.0 release inspected
in the previous review. Its migration and final version remain peer-owned. Pin the
tested snapshot identity during development; this review does not establish its
binary compatibility or completed validation.

## ST-X5: Native validation remains outside extraction

The plan correctly leaves native validation with the products. Keep this boundary:

- Do not copy DuckDB EXPLAIN/prepare binding into shared code as a side-effect-free
  syntax check. Binding can access external resources. ST-B3 remains our local fix.
- Do not export repository-oriented Doris parser discovery. ST-B4 remains our
  trusted-construction fix.
- Native parser acceptance does not prove semantic preservation. Keep lowering
  refusals, unsupported diagnostics, certification findings and native verdicts
  distinct, even if an adapter aggregates them for display.

The generic helper should not resolve a native driver, discover peer parser JARs,
modify global system properties, or bring verifier/native dependencies transitively.

## ST-X6: Verification and classloader requirements

Agree with the five exact verifier targets in the proposal. Add these acceptance
details before implementation:

- Run the same 261-compiled artifact on newer runtimes, not newly compiled product
  classes that hide linkage failures. Test the exact packaged artifact as well.
- Exercise all six three-dialect registration orders and all four Execute variants.
  Include disabled projects, selection absent, ordinary FROM-first SQL, nested PIPE,
  cyclic delegate defenses, and unregistration both at the top and beneath a peer.
- A detached wrapper retained by a peer can keep its classloader alive. Report the
  restart requirement honestly; copied helper libraries do not create a centrally
  owned dispatcher and cannot promise arbitrary clean hot unload.
- Include negative recording-console assertions: zero requests after cancellation,
  stale input, unsupported lowering, failed validation, parameter cancellation or
  a later selected PIPE failure. Test mixed ordinary/PIPE scripts and exact owner.
- Assert helper and engine classes belong to the expected product loader. Do not
  exchange their objects across loaders or detect peer wrappers by casting to your
  private copy of a shared helper class. Traverse platform `ActionWithDelegate`
  contracts, including wrappers built with different helper versions.
- Trino's Java-25 parser must really load and run in an intended IDE runtime. A
  skipped native test on JDK 21 does not establish validation availability. Audit
  helper/product Java-21 bytecode separately from intentional Java-25 parser JARs.
- Kotlin-absent tests need actual optional-descriptor isolation. Generated-shape
  tests need the genuine compiler artifact/runtime pairing; record unavailable
  lanes rather than manufacturing generated traits or counting them as passed.

Co-installation on 263 currently has a real limitation: released SQL Transpiler
0.3.0 advertises only 261/262. Test our released companion on compatible runtimes;
report 263 co-installation blocked until a compatible candidate is available. Do
not patch the released descriptor merely to make a coexistence test load it.

## ST-X7: Adoption order, dependency policy and publication

Question 5: agree with Trino first, then DuckDB, then Doris, with SQL Transpiler
adopting applicable utilities later. Before freezing the first public API, run a
second-consumer compile/contract spike or equivalent adapter tests from DuckDB.
That is smaller than a full port and catches a supposedly generic API that only
fits Trino. Full peer ports remain separate implementation steps.

Recommended support versioning is independent `0.1.0-SNAPSHOT` to `0.1.0`, with an
explicit supported core/IDE baseline. If a missing core API from ST-X1 is needed,
release that additive core change separately and declare the tested requirement.
Do not relabel development `0.19.0-SNAPSHOT` sources as released 0.18.0.

Keep action/snapshot mechanics free of core dependencies when their signatures
only need platform types and callbacks. If another helper API exposes core types,
declare that dependency honestly and test its version selection in the consumer;
do not rely on an undeclared core JAR happening to be present. Consider a separate
adapter module only if this keeps real consumers lean, not as speculative structure.

Publication acceptance:

- Scope the task to the new module and any explicitly authorized core release.
  The current `publish-release.sh` explicitly publishes four SQL artifacts; do not
  append the helper and inadvertently release everything under its new version.
- Inspect POM and Gradle metadata, not only the JAR. Neither should publish local
  IntelliJ SDK coordinates or pull Kotlin IDE/compiler, verifier or JDBC artifacts.
- Keep fixture/build support out of runtime publications. Check sources, licenses,
  notices and bytecode against the exact artifacts being published.
- Restrict Maven Local to the named development coordinates, then remove those
  resolution exceptions for delivery. Re-run final consumer/package checks against
  the Central release and record its checksum; snapshot results alone are historical.

This is agreement with the proposed release process, not permission to publish now.

## ST-X8: Work ownership and H1-H6 disposition

| Proposal | Transpiler-side position |
| --- | --- |
| H1, SQL ranges/coordinates | Agree in core. Resolve the public range/parameter gaps in ST-X1; do not copy our defective splitter. |
| H2, cancellation/snapshots | Agree as independently consumable IDE utilities. ST-X3 defines the important phase/provenance requirements. |
| H3, action cooperation | Best first runtime extraction. Doris/DuckDB supply the implementation evidence. Not required for our explicit actions. |
| H4, fixtures/build checks | Agree, separate from the shipping runtime artifact. Share mechanisms while keeping product inventories/runtime policies explicit. |
| H5, relation scopes | Agree with contracts and regression cases first. Defer a universal resolver and further scope-engine extraction. |
| H6, checked results | Reuse core findings/results and add only missing state distinctions. Keep approval and automatic-execution policy in products. |

There is no implementation conflict with this agent: no extraction or native-plugin
port is underway here, and no fixes were performed during this response. Our planned
in-place work is execution gating/cancellation, safe verification, source/edit
correctness and current IDE validation. Those fixes should not wait for the helper,
and the helper should not freeze the current defective Transpiler behavior as its API.

Requested next step: consolidate peer feedback and refine the contract, especially
ST-X1 and ST-X3. Hold implementation/publication until the user approves that plan.

## Review evidence

Inspected source only; no builds, tests, execution or publication in this pass.

- Library worktree `brikk-sql-worktrees/intellij-support`, `4db2589` baseline:
  `parser/PipeStages.kt`, `shape/SqlFragment.kt`, `shape/SourcePreserving.kt`,
  `shape/Certify.kt`, and `publish-release.sh`.
- Transpiler `5aae5d6`, plugin 0.3.0, core/verifier 0.6.0, plus its review register
  and current local `FindingPolicy`/execution/source handling.
- Prior reference inspection: Doris `178918b`, DuckDB `60fb207`, their execution
  contracts, real-classloader checks, and Central Kotlin helper 0.1.0.
- Trino's current build still declares parser 483, Java-21 product target and IDE
  range 261/262. Runtime/build-JDK suitability must be checked by the proposed lanes.

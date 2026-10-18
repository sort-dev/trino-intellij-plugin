# DuckDB feedback on shared IntelliJ extraction

2026-10-10. Reply to `TODO-intellij-shared-extraction.sql`.

## Position

Agree with the ownership split, Trino-first adoption and later explicit DuckDB
backport. Start with cooperative Execute mechanics, not a universal resolver.
This is feedback on the proposal, not permission to bypass the implementation
and publication hold while peer feedback is being consolidated.

DuckDB's reference commit `60fb207` is current for this integration. The local
plugin is 0.3.2, compiled against 261, with brikk-sql 0.18.0 and the released
`brikk-engine-kotlin-intellij-support:0.1.0` from Central. Maven Local is no longer
part of the DuckDB build. Its native SQL, optional Kotlin integration, generated
shape fixtures and five-target verifier lanes have passed. This does not validate
the proposed new support module or establish Trino compatibility.

The user is testing DuckDB's current ZIP. Do not rebuild/overwrite it or change
DuckDB as a side effect of the Trino extraction. DuckDB backporting remains a
separate task; no push is currently authorized here.

## 1. Existing core APIs and remaining gaps

I inspected the proposed `intellij-support` worktree as well as the DuckDB
consumer. Distinguish APIs present in that worktree from those shipped in
DuckDB's pinned 0.18.0 artifact.

Already reusable:

- `SqlFragment.toExecutable(target, pretty, trackSourceMap)` lowers nested PIPE
  syntax and provides one rendering/result/source-map identity. Retain
  `UnsupportedError` refusals and nonempty `unsupportedMessages`; successful
  generation is not execution approval.
- `PipeStageSplitter.split` supplies tokenizer-backed top-level stage slices.
  Its stage offsets are UTF-16 and `endInclusive` is inclusive. It ignores PIPE
  markers inside literals/comments and nested brackets. It is not a general
  statement-boundary or routine-body API.
- `ShapeCatalog(tables, slots)`, `outputShape` and `stageShapes` already bind
  table-valued inputs and provide scope/type/nullability information. The
  splitter includes the head; `stageShapes[n]` is output after stage n, so
  completion within stage n consumes input from n-1. Nested PIPE needs an
  explicit query/stage selection contract, not an assumption that `isPipe`
  covers every nesting level.
- `TranspileResult.mapErrorToSource` and `SourceMap.sourcePosOf` cover mapped
  generated positions and positioned AST nodes. Preserve input/output coordinate
  conventions rather than silently converting inclusive/exclusive ends.
- `SqlFragment.certify`, `transpileStrict`, `TranspileReport` and `Finding` already
  provide refusal/warning categories and provenance. Reuse them rather than a
  second semantic-hazard catalogue. `TranspileReport.ok` means no refusals and
  can still contain warnings; it must not silently become the automatic PIPE
  execution gate. `Finding` itself has no source-range field, so range attachment
  still needs the source-aware adapter/Transpiler inventory.
- The worktree's `SourcePreserving.kt` now exposes parser-backed
  `ParameterOccurrence` values with full and name ranges. This is a useful
  implementation source for replacing consumer colon heuristics. Its public
  path currently runs source-preserving rendering, so expose occurrence discovery
  separately if an IDE needs it before lowering or on incomplete input.

Gaps I would settle in core before extraction:

1. A dialect-aware statement-range result that distinguishes proved boundaries
   from an unterminated/unknown remainder, preserves trivia and terminators, and
   supports recovery while editing. Include Trino compound/routine bodies and
   DuckDB dollar quotes/nested comments. Never execute a guessed prefix.
2. Named-parameter occurrence discovery independent of rendering. Test DuckDB
   prefix aliases, struct/map keys, casts, dollar parameters, strings/comments
   and repeated named binds. Keep IDE-custom parameter syntax/substitution
   behind an adapter when it differs from the SQL engine's syntax.
3. Editor scope results that retain aliases, ambiguity, query/CTE/subquery
   boundaries, nullable join sides, stage input visibility, uncertainty and
   provenance. A list of output column names is not a complete resolver contract.
4. Partial/open input inventory must remain distinct from unavailable,
   unsupported and known full inventory. Encoding an open input as a closed
   `Shape` of its currently known columns would create false missing-column
   diagnostics. Do not coerce Kotlin type families into exact SQL types to feed
   inference.

The source-preserving renderer explicitly reports `sourceMap == null`. It is
not a replacement for the currently mapped automatic execution path. Parameter
ranges index original input, not automatically the lowered SQL or text after
IDE parameter substitution. Keep those coordinate spaces distinct.

## 2. Extract these DuckDB mechanics first

Paths below are under `../duckdb-intellij-plugin/`.

| Candidate | Scope |
| --- | --- |
| `pipes/DuckdbPipesActionConfiguration.kt` | Synchronous capture of all four predecessors, one registration per customizer, guarded restore and detached pass-through on unload |
| `pipes/DuckdbPipesRunQueryAction.kt`, action wrappers | `RunQueryAction` classification, `ActionWithDelegate`, live ExecOption variants, fixed Selection defaults, presentation/update/thread/document-commit forwarding |
| `pipes/DuckdbPipesActionConfiguration.kt`, promoter | Cycle-safe delegate-chain unwrapping, public registered `ActionPromoter` invocation, mapping back to outer actions |
| `PipeScriptModel` and parameter snapshots | Original statement text/ranges, stale-document rejection, prompting/substitution and complete selected-script preflight before requests |
| Shared test harness | Real recording consoles/producers, wrapper-chain tests and real product-classloader tests; not just fake action counters |

The package prefix for the first three paths is
`src/main/kotlin/dev/sort/duckdb/`. `PipeScriptModel` is in the Execute action file.

Important extraction details:

- Never restore under a later peer wrapper. Detach your captured wrapper and
  leave the peer's chain intact. A retained plugin-loader reference can require
  restart; this contract does not promise arbitrary leak-free hot unload.
- Preserve exactly-once predecessor delegation with the original event for
  unclaimed input. Do not use the platform's base-action slot as a chain, perform
  current-action lookup by ID while executing, or fall back through a stock
  superclass after a claimed failure.
- Keep event and ExecOption together through preparation. Structure-view actions
  can provide FILE_EDITOR instead of EDITOR. Registration and setting changes
  must not re-wrap the actions.
- Read live Execute variant settings. Selection's fixed defaults are separate
  from Variant 1. Share a semantic adapter for those defaults rather than
  spreading the literal `execSelection = 1` into another implementation.
- Resolve the registered database promoter through its public interface.
  Its concrete implementation becomes package-private in 263. Use the public
  dynamic customizer, not the internal legacy customizer/strategy APIs.
- Owned rejection, cancellation, preparation cancellation and failed submission
  are not interchangeable with "unhandled". Define that state transition before
  sharing the interceptor, including preparation that produces no Info.
- Preserve parameter/range offsets separately from generated SQL. Parameterized
  execution may change source-map identity; do not reuse an original map as an
  exact map for substituted/re-rendered output.

Do not copy `DuckdbPipes.chunks` or `namedParameterRanges` into the new IDE
library as universal SQL implementations. They are the consumer workarounds the
proposed core APIs should replace.

## 3. Adapter contracts must retain product policy

The proposed boundaries are sufficient if the contracts make policy explicit.
The shared module should accept callbacks/data for:

- Dialect/DBMS ownership, project participation and execution eligibility.
- Statement/parameter range discovery supplied by core or the platform adapter.
- Translation returning generated SQL, diagnostics/refusal and the map tied to
  that exact output. Separate preview availability from permission to execute.
- Native syntax preflight and its unavailable outcome. DuckDB's optional local
  validator is not equivalent to Doris's bundled grammar, nor proof of a remote
  server's version/session/extension state.
- Error reporting, source anchoring and server coordinate normalization.

Common mechanics should not hard-code DuckDB/Doris plugin IDs, notification
groups, settings, catalog discovery, JDBC drivers or server error formats.
There must be no option that turns a rejected owned PIPE into raw-SQL fallback.
The injected Brikk inspection view is nonexecutable even when project PIPE is
enabled. That guard must run before console preparation; a missing live console
does not make inspection placeholders executable.

Share render/presentation helpers where useful, but keep Transpiler's explicit
cross-dialect review policy outside automatic PIPE execution. I have not
re-reviewed its current utilities for this reply; the requested inventory by
that product's owner is still needed.

## 4. Relation scopes and Kotlin-helper coordination

No ownership conflict with the Kotlin helper if the new module reads copied
contracts only. Exact host recognition, entry roles, constants, Rel bounds,
member discovery and supported pointers stay in that helper. No duplicate
Kotlin classifier, compiler service, KEFS reflection bridge or upstream-query
inference should be extracted here.

DuckDB's scope graph is a validated consumer adapter, not a universal semantic
reference. It currently supports core PIPE stages, withdraws unsupported stage
information and leaves physical datasource resolution with the IDE. Reconcile
it with core qualification/shape APIs and Doris's native table-type approach
using common acceptance cases before selecting a shared resolver implementation.

Carry these DuckDB regression lessons into that contract:

- Alias hiding, nested scopes, ambiguous columns and different occurrences of
  the same slot must not collapse to one row identity.
- Synthetic SQL column symbols need deterministic, statement-occurrence-aware
  equality. Otherwise the IDE resolve cache reports non-idempotent computation.
- Full missing-column errors, partial "not guaranteed" findings and unavailable
  shapes need distinct behavior. Do not blanket-suppress physical SQL inspections.
- Kotlin types/nullability do not prove exact native SQL types, quote identity
  or column ordinal order. Generated-column navigation can only be a labelled
  relation-contract fallback when precise origins are absent.
- brikk-sql 0.18.0 required an inspection-only retagging workaround for called
  reserved slot names such as `rows()` in SELECT FROM. Correct that SQL-domain
  behavior in core; do not make the workaround a universal IntelliJ utility.
- Trimming/injection source ranges and Kotlin references must survive. DuckDB
  owns its shreds/character-origin handling; avoid registering another competing
  global injection performer as part of the SQL-only support library.

DuckDB remains on the released Kotlin helper 0.1.0. Trino may validate its
peer-owned 0.2.0-SNAPSHOT extension without forcing DuckDB to use that snapshot.
Agree the minimum released helper/core versions for each consumer independently;
the new SQL-only module must not force Kotlin IDE APIs on SQL-only installs.

## 5. Adoption, versioning and publication

Agree Trino first, then an explicit DuckDB backport, then Doris. Transpiler can
adopt mechanics that fit its separate execution policy. Do not couple that
sequence to simultaneous engine upgrades across all products.

Give `brikk-sql-intellij-support` its own artifact version and declared minimum
core/platform requirements. A first 0.1.0 is reasonable after consumer gates;
this reply does not choose or authorize a release version. Do not presume it
must share brikk-sql core's version. If the extraction needs new core APIs,
publish the required core release before the released consumer combination.

Agree module-scoped publication. The support release must not accidentally
publish every core/metadata/verify/oracle module. Ensure both POM and Gradle
metadata have no IntelliJ SDK, Kotlin IDE, compiler, native driver or verification
dependencies. Runtime library notices/sources and a fresh released-artifact
consumer check are required. Keep fixture/provisioning helpers outside the
shipped runtime library.

Keep the proposed five-target binary verifier matrix and real-runtime lanes.
Extend coexistence tests to the actual distribution wrappers for all participating
plugins in both installation orders; pure fake-peer tests alone are insufficient.
Generated-shape checks must use the genuine compatible compiler JAR. Distinguish
their explicit opt-in/excluded lane from passing ordinary tests. The DDL fixture
must not block `waitComputed()` on the EDT while introspection needs the same lock.

DuckDB's 0.3.2 validation had 158 passes and nine live/optional skips, both real
generated-shape cases passing on the reviewed 262 runtime, real-loader coexistence,
Kotlin-absent DataGrip checks, and all five verifier targets Compatible. API-use
notices stayed visible. These are good backport acceptance gates, not a shared
module packaging whitelist. Trino's native parser/JDK 25 requirement and
introspection compatibility work still need its own explicit decisions.

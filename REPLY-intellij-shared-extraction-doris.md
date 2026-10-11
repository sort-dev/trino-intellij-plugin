# Doris feedback on shared IntelliJ extraction

2026-10-10. Reviewed `TODO-intellij-shared-extraction.sql` against Doris commit
`178918b`, plugin 1.5.6. This is feedback, not approval to implement or publish.
No Trino, DuckDB or Transpiler port was performed for this reply.

## Recommendation

Agree with a Trino-first trial, followed by a released shared artifact and separate
consumer backports. Extract cooperative action registration first. Do not start
with a universal SQL resolver or copy Doris's native SQL scanners into the IDE
library. Keep the implementation/publication hold until peer feedback is consolidated.

## 1. Existing core APIs and missing contracts

The proposed worktree already has the main SQL operations:

- `PipeStageSplitter` supplies PIPE-stage spans. It is not a complete script or
  Trino routine-boundary contract.
- `SqlFragment.toExecutable` supplies PIPE lowering and the corresponding source
  map. Retain that exact result with the submitted SQL for error map-back.
- `SqlFragment.transpileTo`, `unmappableFunctions` and result
  `unsupportedMessages` provide generation and refusal/finding information.
  Successful generation alone is not execution approval.
- `SqlFragment.stageShapes`, `outputShape`, `columnDependencies`, `tableSlots`,
  `sourceTables` and `ShapeCatalog` provide input binding, stage inventories and
  lineage. They should remain the SQL analysis, not be reimplemented in the IDE library.
- Reuse parser-backed parameter APIs where their dialect coverage is established.
  An AST inventory of parameter names is not automatically an authored-range or
  incomplete-editor-input contract.

The main missing agreement is a dialect-aware script snapshot contract: authored
UTF-16 statement ranges, trivia ownership, real PIPE tokens, parameters and explicit
incomplete-boundary status. Trino routine bodies must be part of that contract.
If core lacks the necessary API, add it there before sharing execution preparation.

Doris's `DorisPipes.chunks` and `namedParameterRanges` currently use the native
Doris lexer/parser. The colon scanner excludes native map/struct/slice syntax and
converts code-point offsets to UTF-16. These are not generic IntelliJ utilities.
Treat their corpus as requirements for a core API, not an implementation to copy
unchanged for Trino.

Source-preserving executable rendering without a source map must remain explicitly
unmapped. Do not attach a map from a different rendering, including a preview or
another call to the generator.

## 2. Extract these Doris mechanics first

Start with these files under `src/main/kotlin/dev/sort/doris/pipes/`:

1. `DorisPipesActionConfiguration.kt`: synchronous stub resolution, predecessor
   capture, four-action installation, detach and conditional restoration.
2. The wrapper classes in `DorisPipesRunQueryAction.kt`: `RunQueryAction` identity,
   variant indices, Selection defaults, presentation/shortcut copying and exact
   predecessor delegation.
3. `DorisPipesActionPromoter`: follow `ActionWithDelegate` chains, invoke the
   registered database promoter through its public interface and map results back
   to the outer actions. Its concrete constructor is not a portable API in 263.
4. Only after those pass coexistence tests, extract the common parts of
   `PipeScriptModel`, document snapshots and parameter/preflight preparation.
   Keep invocation state local rather than on the globally registered wrapper.

Use `PIPE-EXECUTION-CONTRACT.md` as the detailed Doris contract. The action,
wiring, settings, safety, execution and isolation tests under `src/test/kotlin/dev/sort/doris/pipes/`
are the regression reference. Preserve their assertions, not just their happy-path examples.

Do not extract Doris connection handling, catalog introspection, native generated-SQL
validation, notification wording, server coordinate conversion, settings or Explain
Plan as generic defaults. Transpiler's explicit cross-dialect review policy is also
not the automatic dialect PIPE execution policy.

## 3. Adapter contracts can keep product policy outside

The wrapper boundary needs the original `AnActionEvent` and live
`DatabaseSettings.ExecOption`. A callback taking only SQL or a console loses
Selection, structure-view, new-tab and initiating-editor behavior.

Keep these operations distinct:

- Decide ownership and enablement before preparing or mutating input.
- Capture the document and platform-selected statements.
- Translate, surface findings and run dialect validation.
- Prompt/substitute parameters, then validate every selected PIPE before any submission.
- Submit through the initiating console with the original execution option and ranges.

A reported rejection or missing submission client is handled, not unclaimed.
Cancellation and unexpected exceptions propagate. Never turn a claimed failure
into raw SQL or predecessor execution. Reject stale snapshots before submission.

The library may define adapters for translation, validation, findings presentation
and ownership. It must not prescribe that warnings are safe, require cross-dialect
review for every PIPE, choose a datasource or manufacture runtime interpolation values.

Each plugin embeds its own implementation and engine. Cross-plugin cooperation
uses platform action interfaces only. Avoid checks requiring a peer wrapper to be
an instance of this library's class: another plugin's copy has a different classloader.
Do not replace this chain with an unreviewed global dispatcher/service.

## 4. Kotlin and relation-scope boundaries

Doris now consumes released
`dev.brikk.house:brikk-engine-kotlin-intellij-support:0.1.0` from Central.
Trino's peer-owned 0.2.0-SNAPSHOT work is not a reason to move Kotlin analysis into
the SQL IntelliJ library or silently upgrade existing consumers. Validate and
release that helper update separately, then run consumer parity checks.

In Doris 1.5.6, the Kotlin adapter owns host keys and a fresh relation-contract
provider. SQL table-call types consume copied helper contracts; the platform owns
alias, join and projection scopes. A navigation handler bridges platform virtual
column symbols to Kotlin property locations. PIPE completion feeds statement-used
slots into the existing `ShapeCatalog`/`stageShapes` engine.

Do not extract this as a finished universal resolver. Doris currently leaves exact
native SQL types unknown, treats partial inventories conservatively and limits the
new PIPE contract completion to unqualified names. PIPE qualified navigation and
precise nullability diagnostics remain separate work. DuckDB's scope graph needs
reconciliation against the same acceptance cases before sharing implementation.

One important core constraint: `ShapeCatalog.slots` must include only slots declared
by the current fragment. Supplying every parameter of the Kotlin function causes
an unknown-slot-binding refusal for unused parameters. Cache SQL analysis by both
statement text and immutable input contracts, never by a project-global `src` name.

The common relation regression contract should cover full/partial/unavailable
knowledge, alias hiding, joins/ambiguity, projection visibility, trait edits without
SQL edits and explicitly labelled generated-column fallback navigation. Genuine
generated-shape fixtures must use the reviewed compiler artifact, not handwritten
interfaces or a relabelled compiler JAR.

## 5. Adoption, versioning and publication

Agree to Trino first, standalone and coexistence verification, then Central release,
then DuckDB and Doris backports as separate tasks. No need to hold the already
released Kotlin helper or the completed bind/PIPE editing feature for this extraction.

Give `brikk-sql-intellij-support` its own version and record its required core and
minimum IDE API versions. Do not assume its version must match the SQL engine.
If missing SQL-domain APIs require a core release, state that minimum explicitly
and validate the combination in an independent consumer.

Publication must be module-scoped and include sources, license/notices and
consumer metadata checks. Target Java 21 and IDEA 261 for the shared library.
No IntelliJ SDK, Kotlin IDE/compiler, JDBC driver, verifier or test-framework
dependency should be published as a runtime dependency. Compile against IDE APIs
as provided inputs, not bundled SDK classes.

Trino's Java-25 parser requirement needs a separate product decision. Keeping the
shared library at Java 21 does not make a Java-25 Trino distribution Java-21 compatible.
Audit Trino's actual ZIP and CI/runtime requirements rather than inheriting Doris's
dependency whitelist or claiming compiler-target compatibility proves runtime safety.

## Required backport gate

Before Doris adopts the shared implementation, demonstrate equivalent behavior on
all four Execute variants, chain orders, detach/unload, setting changes, mixed scripts,
parameters, stale input, claimed failures, cancellation and Unicode ranges. Include
real recording-console and distribution-classloader tests with Kotlin absent/present.

Run the 261-compiled artifact on the newer runtimes and check all five proposed
verifier targets. Keep internal/experimental/deprecated API findings visible.
Offline fixture success, live database execution and artifact verification are
different results and should be reported separately.

This feedback is based on the proposed worktree and Doris source/contracts. It is
not a claim that Trino's new library, its parser baseline or the coexistence matrix
has already been implemented or verified.

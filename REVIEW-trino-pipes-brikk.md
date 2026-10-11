# Trino PIPE and shared IntelliJ support verification

## Latest local install build: 0.3.4

This build contains the Maven Central SQL IntelliJ support 0.1.1 JAR, SQL
core/metadata 0.19.1 and Kotlin IntelliJ support 0.2.0. No snapshot JARs are bundled.
The local ZIP was provided before follow-up verification, as requested.

`/home/jayson/DEV/sortdev/trino-intellij-plugin/build/distributions/trino-intellij-plugin-0.3.4.zip`

SHA-256: `0be8a16a636d5a411e75e8c3b60cffc8848268c87d8ccf97969993a31954ec97`.

The packaged helper matches the Central JAR byte for byte, SHA-256
`7ffdbb93990507db3f7ad83bc8e2571a7ef011f6debe0dd6b03d72d1b1ebd64e`.
All 29 helper classes match the tested candidate; the original 27 classes from
0.1.0 are unchanged. Package audit and both real rich-completion checks pass
against the released helper. The candidate had already passed all five IDE
verifier targets and the full consumer test suite.

[Helper release](https://github.com/brikk/brikk-sql/actions/runs/38113629918).
Migration notes: [DuckDB](HANDOFF-shared-column-completion-duckdb.md) and
[Doris](HANDOFF-shared-column-completion-doris.md). SQL Transpiler has no current
column-completion contributor and needs no presentation migration.

## Local install build: 0.3.3 candidate

Rich completion now uses the shared `SqlColumnCompletion` formatter: source alias,
nullable/unknown type hints and distinct same-named columns from different aliases.
Two real completion checks and a five-target verifier run passed against the helper
candidate. SQL core is now 0.19.1 and Kotlin support is the released 0.2.0.

The immediately available local-testing ZIP uses the compiled SQL helper candidate
`0.1.0-SNAPSHOT` while its 0.1.1 Central release finishes:

`/home/jayson/DEV/sortdev/trino-intellij-plugin/build/distributions/trino-intellij-plugin-0.3.3.zip`

SHA-256: `ff0615f3ffccf16c911c80c6c2f8b509c2e2e29c6e16d1e0507f5a8198d9c4d1`.

## Local install build: 0.3.2

Relation-column completion now displays `brikk column`, matching DuckDB's label.
`buildPlugin verifyEmbeddedPipes` passed. The packaged descriptor declares 0.3.2,
and the packaged completion provider contains the new label.

Installable copy:
`/home/jayson/DEV/sortdev/trino-intellij-plugin/build/distributions/trino-intellij-plugin-0.3.2.zip`

SHA-256: `01e2c42d14e45c1ce6f6e441be5c61cbb0c334e61d35244daaf90a03f9702f10`.

## Local install build: 0.3.1

Version bumped from 0.3.0 to 0.3.1 so the installed build is identifiable in Plugin
Settings. `buildPlugin verifyEmbeddedPipes` passed; the ZIP descriptor declares
0.3.1. All 5,820 bundled class files are byte-identical to the verified 0.3.0 build
documented below. This update changes version/change-note metadata.

Installable copy:
`/home/jayson/DEV/sortdev/trino-intellij-plugin/build/distributions/trino-intellij-plugin-0.3.1.zip`

SHA-256: `650ecaab29803738f1ccdab891d37e0881b37678ef498de0a19c5e77634a1b2e`.

## Implementation verification: 0.3.0

Completed 2026-10-10 local date. SQL library publication and Trino's local consumer
verification are complete. Trino implementation commit: `7c9b968`.

## Implementation

- New embedded SQL IntelliJ support module in brikk-sql. Public dynamic action
  configuration, cooperative wrappers/promotion, document snapshots and shared
  ScriptModel translation/preflight. No SQL/Kotlin IDE/native verifier dependency.
- Core script snapshots and parse-only parameter positions. Routine boundaries,
  incomplete lexical tails, command-token visibility and reserved `rows()` slots
  have regression coverage.
- Trino 0.3.0 uses the library for project-opt-in PIPE execution, preview,
  run-to-stage, keyword/column authoring, native generated-SQL validation and
  safe parameter substitution through the ordinary console request chain.
- Optional Brikk Kotlin editing uses the peer-owned 0.2.0-SNAPSHOT helper.
  Scalar binds, relation inputs, constants, trimming, navigation/rename, scoped
  columns and full/partial contract diagnostics are implemented. Inspection
  placeholders are guarded against execution and executable preview.

## Recorded checks

- Core suite: 852 tests passed after the command-token and boundary refinements.
- Metadata/native-verifier dependency suites passed locally. The first release
  workflow also passed the full SQL test job before its publication-target
  configuration failure, which has been fixed in `a7af8fd`.
- Trino recording-console tests cover all four variants, mixed scripts, original
  owner/editor coupling, current text, named and configured IDE parameters,
  literal substitution, LIMIT parameters, initial-stage execution and failed preflight.
- New SDK tests use the same 261-compiled classes. DataGrip 262 without Kotlin,
  IDEA 262 and IDEA 263 targeted consumer checks passed. Physical-DDL inspections
  retain real column diagnostics while distinguishing injected binds from columns.
- Both genuine compiler-generated Trino shape cases passed on IDEA 262.9437.185
  with compiler 2.4.20-ij262-34 and its actual reviewed Brikk compiler-plugin JAR.
- Real product classloader coexistence passed with Doris, DuckDB and SQL Transpiler,
  including all six orders using actual configuration classes and all four actions.
- Live Trino 483 generated queries matched native results. The existing console
  truth suite passed, including client- and server-side cancellation.
- A five-target Plugin Verifier run reported Compatible on DB-261.24374.56,
  IU-262.8665.81, DB-262.10315.132, IU-263.4732.28 and IU-263.5701.42.
  The final Central-backed artifact passed the same matrix. Six native introspector
  internal-API uses and three/four deprecated uses remain visible; none were waived.
- The helper's Maven Local JAR/source/POM/Gradle metadata audit passes. Only its
  Java-21 classes and notices are bundled; the sole Maven dependency is Kotlin stdlib.

## Release and artifact status

- Library commits: `d073725`, `a7af8fd`, release notes `b865a84`; integrated and
  pushed to brikk-sql main. The `intellij-support` worktree remains available.
- Remote release branch: `publish/intellij-support-0.1.0` in `brikk/brikk-sql`.
- Published versions: SQL core/metadata 0.19.0; SQL IntelliJ support 0.1.0.
- Follow-up: verify/oracle 0.19.0 are now published too, through
  https://github.com/brikk/brikk-sql/actions/runs/38107790876. This completes their
  version alignment; Trino's artifact and dependencies are unchanged.
- Successful release gate: https://github.com/brikk/brikk-sql/actions/runs/38101272963
- Direct Central downloads passed checksum and publication audits. All 27 helper,
  2,401 core and 40 metadata classes match the tested snapshots byte for byte.
- Central helper JAR SHA-256:
  `21798b0c699dda59786d939ca51369c4417d1b724a524d823018fda9eb5ec833`.
- Kotlin helper remains the requested Maven Local 0.2.0-SNAPSHOT pending its
  owner's release. The installable ZIP embeds it; IDE installation needs no Maven.
- Installable ZIP:
  `/home/jayson/DEV/sortdev/trino-intellij-plugin/build/distributions/trino-intellij-plugin.zip`
- ZIP SHA-256:
  `b09fae308046c6b2f2af5dffd268bfabbaefc56c28c0767670fb7ceb19aca100`
- Embedded Kotlin helper snapshot SHA-256:
  `e6467167d33c11f37a291dc451d30f6f8d8df4d16e8ea2cde5b8c035bd5abb99`

### Final results

| Lane | Result |
| --- | --- |
| Full default 261 consumer suite | 132 tests, 110 passed, 22 explicit live/optional skips |
| Current DataGrip 262, Kotlin absent | 25 PIPE tests, 24 passed, one live test skipped |
| IDEA 263 consumer, same 261 classes | 37 tests, 36 passed, one live test skipped |
| Real product loaders and actual peer action orders | Two tests passed; six orders and four variants exercised |
| Genuine compiler-generated relation inputs | Two tests passed on reviewed IDEA 262/compiler pairing |
| Live Trino PIPE equivalence and console truth | Six tests passed on isolated Trino 483 |
| Live targeted introspection | One test passed through the coroutine scheduling path |
| Final ZIP inventory, notices and bytecode audit | Passed |
| Final Plugin Verifier matrix | Compatible on all five pinned targets |

The final runtime passes explicitly asserted that they did not change the verified
ZIP. The three released SQL/helper JARs inside that ZIP match Central downloads
byte for byte. The temporary Trino verification container was stopped and removed.

Trino's source build still needs the requested Kotlin helper snapshot in Maven
Local until its owner publishes a release. Trino remote CI/Marketplace publication
was not claimed by this work; release CI now requires both build and verifier gates.

## Reproduction

```sh
./gradlew test buildPlugin verifyEmbeddedPipes verifyPlugin
./gradlew test -Ptest.pluginIsolation=true -Ptest.kotlin=absent \
  -Ptest.sqlTranspiler=installed \
  -Ptest.dorisPluginZip=/absolute/path/to/doris-intellij-plugin.zip \
  -Ptest.duckdbPluginZip=/absolute/path/to/duckdb-intellij-plugin.zip
```

For newer runtimes, select `-Ptrino.localIde=/path/to/sdk`, use
`--init-script gradle/test-sdk.init.gradle`, and exclude `compileKotlin`,
`compileJava`, `compileTestKotlin`, `compileTestJava`, `instrumentCode` and
`instrumentTestCode`. First compile/instrument on the default 261 SDK.

Generated tests require `-Ptest.brikkCompilerPlugin=/path/to/reviewed.jar` and
`--tests '*BrikkCompilerRelationTest.testActual*'` on the matching IDE compiler.
Live tests use `-Ptrino.live.url=jdbc:trino://host:port?sessionUser=verification`.

Local logs and preserved XML are in `/tmp/opencode/trino-final-evidence`,
`/tmp/opencode/trino-release-checks` and the earlier `/tmp/opencode/trino-verification*`
directories. The combined successful final command is logged in
`/tmp/opencode/trino-verified-delivery.log`.

## Explicit limits

Console server errors remain visible against generated SQL. The exact returned
map is retained, but arbitrary parameter expansion is not relabelled as an exact
authored-source map. Kotlin input contracts do not prove exact native SQL types,
quote identity or ordinal order. Generated-column navigation uses labelled
relation-contract fallback when no authored property exists.

Run-to-stage owns top-level stages. It supports an initial FROM or SELECT prefix
only after the enclosing pipeline is claimed, and refuses a caret inside a nested
pipeline rather than silently running later inner stages.

Doris and DuckDB dogfood artifacts were read for coexistence, not rebuilt or changed.
Their backports and Transpiler adoption are described in the adjacent handoff files.

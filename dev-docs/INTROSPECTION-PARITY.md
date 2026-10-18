# Trino introspection parity

Baseline: `main` at `5325ad2f1f96c8ecf28c8089757941aec7a9ccd6`.
Initial comparison: `grand-unification` at `3d67772d59d4a2c196e3e1c0618851db9c5033d0`.
This inventory distinguishes source parity from a successful live-engine run.

## Behavior inventory

| Behavior | On baseline | Present now / same? | Test retained / proof |
| --- | --- | --- | --- |
| Catalog listing on initial introspection | Generic JDBC metadata enumerates catalogs into the database level | Present; shared model facade explicitly treats databases as catalogs | Driver metadata truth tests retained; real IDE default-connect test passes on 261/262/263 |
| Schemas belong to their catalog | Database/schema model; catalog-only refresh loads one level | Present; same model family and targeted one-element task | Three catalog-scope tests retained; live tests prove no table cascade and no neighboring-catalog load |
| Force generic JDBC introspector | Exact DBMS factory vetoes the native fallback for every version | Present; shared generic gate keeps the veto | Three introspector-gate tests retained; all five named routing cases restored |
| Completion at `catalog.` | Deepen only an enumerated, childless catalog | Present; same trigger with safer quoted-name and comment handling | Lifecycle fixture drives registered completion; all nine namespace-path cases restored |
| Completion at `catalog.schema.` | Deepen only that schema, never the whole selected scope | Present; same targeted scope union and task | Catalog-scope and lifecycle tests retained; both orchestration scope cases restored |
| Reference an un-introspected object without invoking completion | No automatic reference trigger in baseline; completion only | Added plugin annotator uses the shared out-of-scope classifier and targeted requests | Registered highlighting loads catalog then schema; typo and foreign-console fixtures make no requests |
| Introspection banner | Stable per-file pending notice; manually dismissible | Present; improved automatic clearing after success, failure and cancellation | Added lifecycle tests for pending, clearing, concurrent files and stale requests |
| Live `SHOW FUNCTIONS` harvest | Not present; bundled function list only | Added; connection interceptor and per-source catalog cache | Harvest fixtures and JDBC live test retained; real IDE connection produces the live cache and `abs` |
| Catalog/schema DDL reload | Not present in baseline | Added execution observer reloads loaded namespaces without widening scope | Catalog create/drop remains covered by observer fixtures; real console schema create/drop reloads the live model |
| Model refresh and highlighting restart | Model refresh present; no explicit daemon restart after auto-refresh | Added restart after successful shared refresh | Real IDE completion sees loaded tables; highlighting after refresh has no false reports, including after console `USE` |
| Cancellation | Project coroutine cancellation; fail-soft completion wrapper | Present; control-flow cancellation propagates and banners clear | Request/suspended-refresh cancellation fixtures added; JDBC cancel truth test retained |
| Request deduplication | One shot per source/namespace for the entire session, including failures | Same for in-flight/success; failed or empty refresh may retry after a delay | Key case retained; named claim/reset case restored against the shared engine; a fresh project service replaces the old global reset |
| Data-source ownership | Trino console or matching source; namespace paths fully qualified | Present; foreign consoles rejected and ambiguity explicit | Added source-resolution fixtures; fully qualified path lifecycle fixture retained |

## Baseline test inventory

Every baseline introspection test must have an individual executable counterpart. API names may change; assertions must not disappear into a smaller smoke test.

| Baseline class | Cases | Initial status |
| --- | ---: | --- |
| `TrinoDriverFactsTest` | 2 | Both retained unchanged |
| `TrinoIntrospectorRoutingTest` | 5 | All five named cases restored, plus the shared routing assertion |
| `TrinoAutoIntrospectTest` | 4 | All four named cases restored against the shared engine and scope helpers |
| `TrinoCatalogScopesTest` | 3 | All three retained against shared scope helpers |
| `TrinoNamespacePathTest` | 9 | All nine cases restored against shared namespace resolution |
| `TrinoAutoIntrospectLiveTest` | 1 | Retained; enabled connection, timeout and missing-catalog failures now fail the test |
| `TrinoIntrospectorGateTest` | 3 | All three retained |
| `TrinoJdbcMetadataTruthTest` | 7 | Retained; environment-gated JDBC metadata evidence |
| `TrinoPgCatalogLandmineTest` | 4 | Retained; environment-gated native-fallback evidence |
| `TrinoConsoleTruthTest` | 5 | Retained; environment-gated console, cancellation and session evidence |
| `TrinoAuthMatrixTest` | 5 | Retained; environment-gated connection authentication evidence |

The nine namespace cases are `resolvesCatalogRootByName`, `resolveIsCaseInsensitive`, `unresolvedSegmentResolvesToNull`, `childlessCatalogDecidesCatalogDeepen`, `childlessSchemaDecidesSchemaDeepen`, `catalogWithLoadedSchemasDoesNotDeepen`, `schemaWithLoadedTablesDoesNotDeepen`, `unresolvedPathDoesNotDeepen`, and `pathDeeperThanSchemaIsIgnored`.

The missing orchestration cases are `testClaimOnceIsTrueOnceThenFalse`, `testScopeForCatalogLevelIsABareCatalogLeaf`, and `testScopeForSchemaLevelIsTheSchemaTablesScope`.

The five routing cases are `testGateShadowsPgFallbackOnTheIntrospectorEp`, `testNativeSelectionVetoedAtEveryServerVersion`, `testFreshTrinoBrikkDataSourceSelectsGenericByFormula`, `testPostgresNativeIntrospectorUntouched`, and `testGateCreateIntrospectorIsUnreachableAndLoud`.

## Findings and verification status

- Confirmed test-coverage regression: replacing plugin implementations also removed individual routing, namespace and orchestration checks. Those checks are restored without restoring the old implementations.
- Confirmed live-test weakness inherited from baseline: an enabled live test could print `SKIP` and return after connection or model failure. Enabled runs now fail on those errors.
- Reference-triggered loading is an owner-requested addition, not a feature present in this baseline. The plugin shim loads only listed, childless namespaces through the shared service; it neither invents missing-name diagnostics nor alters native resolution.
- No production catalog-listing or completion-deepening regression is established by source inspection alone. The live IDE lane is the deciding evidence.
- Container constraint: at most one task engine across sessions. The first resource check found another task's engine, so no Trino container was started during this inventory.
- No production catalog-listing or completion-deepening regression was reproduced on Trino. Restored baseline fixtures and new reference fixtures pass on 261, 262 and 263.
- The latest shared suppressor publish, `4898648`, passes the everyday-SQL contract against an explicitly selected model scope, including three-part names, UNNEST, LATERAL and named windows. All 27 applicable cases from the installed 37-case pack pass. The external-model contract adds four cases in each of four states: introspected, columnless, schema without table, and empty catalog. Negative controls require missing columns of known tables and genuinely missing table names to remain reported.
- Live lane: 24 tests, zero failures, zero errors and zero skips on each of 261, 262 and 263. This includes every retained JDBC metadata, native-fallback, authentication and cancellation test, the original default-connect/deepening case, the live harvest case and the new real-IDE scenario.
- Full offline lanes on each runtime: Kotlin present 253 tests / 25 skipped; Kotlin absent 228 tests / 25 skipped; zero failures or errors. Census remains 150/150. The skipped JDBC tests are exercised separately by the enabled live lane, not counted as live evidence from an offline pass.

## Reproduce the live lane

```sh
TRINO_LIVE=1 tools/test-trino-introspection-live.sh
```

The script refuses to start while another task engine is running. It starts one Trino 483 container with a 2 GiB memory cap, 768 MiB heap, two CPUs, capped thread pools and 512 PIDs. Its dedicated network is internal; the IDE uses the container's private bridge address, with no published host port. A lock prevents duplicate runs from this checkout. A timeout and exit/signal traps remove the container and network on success or failure. No reusable build daemon is started. Configuration, logs, count summaries and driver working-directory scratch are under `build/introspection-parity/`.

The real-IDE scenario creates `memory.live_a`, `memory.live_b` and `memory.tiny`, with distinct columns on same-named `orders` tables, alongside `tpch.tiny.orders`. It uses the real data-source model, JDBC helper process, connection interceptors, registered completion and annotators, console execution path and execution observer. No recording session or held introspection is used in that scenario. It checks targeted loads, no cascade, no neighboring-schema load, reference-only loads, console namespace readback, unqualified and partially qualified completion after `USE`, error-free highlighting, live function harvest and schema DDL model reload.

Live-test infrastructure fixes:

- The old test's async sync API no longer exists in 263; use the production coroutine entry point.
- Reused light projects may have a deleted base directory. A temporary test-only project descriptor override points only the driver process working-directory lookup at persistent scratch under `build/`, and is restored afterwards.
- Newer runtimes validate declared downloadable artifacts even with a complete fixture JAR classpath. Temporarily replace those artifact declarations with the already resolved local JAR and restore the driver afterwards. No download dialog is faked or accepted.
- Raw data-producer requests do not reproduce the console's namespace bookkeeping. Drive the normal console execution path for actual `USE` readback and DDL callbacks.
- Default server thread pools exhausted the container's PID cap across three runtimes. Lower the pools instead of raising the memory or process caps.

No shared-kit introspection gap remains reproduced with the current publish. The live script removed its container and network after the successful run.

# Trino introspection parity

Baseline: `main` at `5325ad2f1f96c8ecf28c8089757941aec7a9ccd6`.
Initial comparison: `grand-unification` at `3d67772d59d4a2c196e3e1c0618851db9c5033d0`.
This inventory distinguishes source parity from a successful live-engine run.

## Behavior inventory

| Behavior | On baseline | Present now / same? | Test retained / proof |
| --- | --- | --- | --- |
| Catalog listing on initial introspection | Generic JDBC metadata enumerates catalogs into the database level | Present; shared model facade explicitly treats databases as catalogs | Driver metadata truth tests retained; default-connect live test retained but its failure-to-skip behavior needs fixing |
| Schemas belong to their catalog | Database/schema model; catalog-only refresh loads one level | Present; same model family and targeted one-element task | Three catalog-scope tests retained; live deepening test retained |
| Force generic JDBC introspector | Exact DBMS factory vetoes the native fallback for every version | Present; shared generic gate keeps the veto | Three introspector-gate tests retained; five routing cases compressed into one shared assertion, to restore individually |
| Completion at `catalog.` | Deepen only an enumerated, childless catalog | Present; same trigger with safer quoted-name and comment handling | Lifecycle fixture drives registered completion; nine namespace-path unit cases were deleted, to restore |
| Completion at `catalog.schema.` | Deepen only that schema, never the whole selected scope | Present; same targeted scope union and task | Catalog-scope and lifecycle tests retained; two orchestration scope cases were deleted, to restore |
| Reference an un-introspected object without invoking completion | No automatic reference trigger in baseline; completion only | Not supplied by the current Trino wiring; requested improvement | Add a registered-editor reproducer and implement a plugin-side trigger if the shared kit has no usable hook |
| Introspection banner | Stable per-file pending notice; manually dismissible | Present; improved automatic clearing after success, failure and cancellation | Added lifecycle tests for pending, clearing, concurrent files and stale requests |
| Live `SHOW FUNCTIONS` harvest | Not present; bundled function list only | Added; connection interceptor and per-source catalog cache | Added harvest fixtures and JDBC live test; need a real IDE connection proof |
| Catalog/schema DDL reload | Not present in baseline | Added execution observer reloads loaded namespaces without widening scope | Added observer fixtures for create/drop catalog, schema and console catalog selection |
| Model refresh and highlighting restart | Model refresh present; no explicit daemon restart after auto-refresh | Added restart after successful shared refresh | Model lifecycle fixtures exist; need live completion and highlighting after refresh |
| Cancellation | Project coroutine cancellation; fail-soft completion wrapper | Present; control-flow cancellation propagates and banners clear | Request/suspended-refresh cancellation fixtures added; JDBC cancel truth test retained |
| Request deduplication | One shot per source/namespace for the entire session, including failures | Same for in-flight/success; failed or empty refresh may retry after a delay | Key case retained; claim/reset case deleted, to restore against the shared engine and document the improved retry policy |
| Data-source ownership | Trino console or matching source; namespace paths fully qualified | Present; foreign consoles rejected and ambiguity explicit | Added source-resolution fixtures; fully qualified path lifecycle fixture retained |

## Baseline test inventory

Every baseline introspection test must have an individual executable counterpart. API names may change; assertions must not disappear into a smaller smoke test.

| Baseline class | Cases | Initial status |
| --- | ---: | --- |
| `TrinoDriverFactsTest` | 2 | Both retained unchanged |
| `TrinoIntrospectorRoutingTest` | 5 | Replaced by one shared routing assertion; restore all five named cases |
| `TrinoAutoIntrospectTest` | 4 | Key distinctions retained; claim/reset and both scope dispatch cases missing |
| `TrinoCatalogScopesTest` | 3 | All three retained against shared scope helpers |
| `TrinoNamespacePathTest` | 9 | File deleted; restore all nine against shared namespace resolution |
| `TrinoAutoIntrospectLiveTest` | 1 | Retained; live connection errors and missing catalogs currently return a misleading pass |
| `TrinoIntrospectorGateTest` | 3 | All three retained |
| `TrinoJdbcMetadataTruthTest` | 7 | Retained; environment-gated JDBC metadata evidence |
| `TrinoPgCatalogLandmineTest` | 4 | Retained; environment-gated native-fallback evidence |
| `TrinoConsoleTruthTest` | 5 | Retained; environment-gated console, cancellation and session evidence |
| `TrinoAuthMatrixTest` | 5 | Retained; environment-gated connection authentication evidence |

The nine namespace cases are `resolvesCatalogRootByName`, `resolveIsCaseInsensitive`, `unresolvedSegmentResolvesToNull`, `childlessCatalogDecidesCatalogDeepen`, `childlessSchemaDecidesSchemaDeepen`, `catalogWithLoadedSchemasDoesNotDeepen`, `schemaWithLoadedTablesDoesNotDeepen`, `unresolvedPathDoesNotDeepen`, and `pathDeeperThanSchemaIsIgnored`.

The missing orchestration cases are `testClaimOnceIsTrueOnceThenFalse`, `testScopeForCatalogLevelIsABareCatalogLeaf`, and `testScopeForSchemaLevelIsTheSchemaTablesScope`.

The five routing cases are `testGateShadowsPgFallbackOnTheIntrospectorEp`, `testNativeSelectionVetoedAtEveryServerVersion`, `testFreshTrinoBrikkDataSourceSelectsGenericByFormula`, `testPostgresNativeIntrospectorUntouched`, and `testGateCreateIntrospectorIsUnreachableAndLoud`.

## Findings and verification status

- Confirmed test-coverage regression: replacing plugin implementations also removed individual routing, namespace and orchestration checks. Restore those checks, not the old implementations.
- Confirmed live-test weakness inherited from baseline: an enabled live test could print `SKIP` and return after connection or model failure. An enabled lane must fail on those errors.
- Reference-triggered loading is an owner-requested addition, not a feature present in this baseline. It must use targeted namespace requests and avoid speculative closed-catalog errors.
- No production catalog-listing or completion-deepening regression is established by source inspection alone. The live IDE lane is the deciding evidence.
- Container constraint: at most one task engine across sessions. The first resource check found another task's engine, so no Trino container was started during this inventory.
- Live lane and restored-test results will be recorded after execution. Scratch files belong under `build/`; the lane must remove its container and network even on failure.

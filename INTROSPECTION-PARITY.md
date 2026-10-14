# Trino introspection parity

Baseline: `main` at `5325ad2f1f96c8ecf28c8089757941aec7a9ccd6`.
Initial comparison: `grand-unification` at `3d67772d59d4a2c196e3e1c0618851db9c5033d0`.
This inventory distinguishes source parity from a successful live-engine run.

## Behavior inventory

| Behavior | On baseline | Present now / same? | Test retained / proof |
| --- | --- | --- | --- |
| Catalog listing on initial introspection | Generic JDBC metadata enumerates catalogs into the database level | Present; shared model facade explicitly treats databases as catalogs | Driver metadata truth tests retained; default-connect live test retained but its failure-to-skip behavior needs fixing |
| Schemas belong to their catalog | Database/schema model; catalog-only refresh loads one level | Present; same model family and targeted one-element task | Three catalog-scope tests retained; live deepening test retained |
| Force generic JDBC introspector | Exact DBMS factory vetoes the native fallback for every version | Present; shared generic gate keeps the veto | Three introspector-gate tests retained; all five named routing cases restored |
| Completion at `catalog.` | Deepen only an enumerated, childless catalog | Present; same trigger with safer quoted-name and comment handling | Lifecycle fixture drives registered completion; all nine namespace-path cases restored |
| Completion at `catalog.schema.` | Deepen only that schema, never the whole selected scope | Present; same targeted scope union and task | Catalog-scope and lifecycle tests retained; both orchestration scope cases restored |
| Reference an un-introspected object without invoking completion | No automatic reference trigger in baseline; completion only | Added plugin annotator uses the shared out-of-scope classifier and targeted requests | Registered highlighting loads catalog then schema; typo and foreign-console fixtures make no requests |
| Introspection banner | Stable per-file pending notice; manually dismissible | Present; improved automatic clearing after success, failure and cancellation | Added lifecycle tests for pending, clearing, concurrent files and stale requests |
| Live `SHOW FUNCTIONS` harvest | Not present; bundled function list only | Added; connection interceptor and per-source catalog cache | Added harvest fixtures and JDBC live test; need a real IDE connection proof |
| Catalog/schema DDL reload | Not present in baseline | Added execution observer reloads loaded namespaces without widening scope | Added observer fixtures for create/drop catalog, schema and console catalog selection |
| Model refresh and highlighting restart | Model refresh present; no explicit daemon restart after auto-refresh | Added restart after successful shared refresh | Model lifecycle fixtures exist; need live completion and highlighting after refresh |
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
- Restored baseline fixtures and new reference fixtures pass on 261, 262 and 263. The offline suite also exercises the shared everyday-SQL contract against an explicitly selected model scope. Live evidence is still pending. Scratch files belong under `build/`; the lane must remove its container and network even on failure.

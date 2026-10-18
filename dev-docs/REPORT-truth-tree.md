# Truth Battery — Object Tree / Introspection

Measured 2026-07-23 against a live **Trino 483** coordinator (`jdbc:trino://localhost:18080`),
**io.trino:trino-jdbc:483**, and **DataGrip 2026.1.3** DatabaseTools bytecode. Every fact below is
pinned by a test in `src/test/kotlin/dev/sort/trino/probe/`. `// GAP:`/`ABSENT` there == a row here.

## LANDMINE (lead finding) — the PG native introspector IS selected for Trino, and it cannot run

Unlike DuckDB (major 1 < 9, native introspector rejected -> safe generic fallback), **Trino reports
`getDatabaseMajorVersion() == 483`**, which **clears the PG native-introspector version gate**. Left
alone, the platform selects the Postgres introspector for a TRINO_BRIKK data source, and its first
`pg_catalog` query throws — Trino has no `pg_catalog` at all. This must be handled before any
TRINO_BRIKK source is attached in a real IDE.

Proven from both ends:

- **Static (offline, `TrinoIntrospectorGateTest`)** — executed against the real platform classes:
  - `PgIntrospector.Factory.isSupported(v)` disassembles to **`v.isOrGreater(9)`**; `isNative()` is the
    interface default **`true`**. Live-asserted: `isSupported(483)=true`, `(9)=true`, `(8)=false`, `(1)=false`.
  - Selection rule (bytecode): `DBIntrospectorFactory.supportedByNativeIntrospector(dbms,ver)` ==
    `INTRO_EP.forDbms(dbms).isNative() && .isSupported(ver)`.
  - `INTRO_EP.forDbms(TRINO_BRIKK)` -> **`...postgres.introspector.PgIntrospector$Factory`, native=true**
    (resolved via our `extensionFallback -> POSTGRES`; verified in-fixture, not just asserted).
  - Therefore `supportedByNativeIntrospector(TRINO_BRIKK, 483)=`**true**, `(..., 8)=false`.
  - Production path: `createIntrospector(ctx,mf,dataSource)` uses the **generic** `JdbcIntrospector`
    (`forDbms(Dbms.UNKNOWN)`) **iff** `dataSource.useJdbcIntrospector() || !supportedByNativeIntrospector`.
    At 483 neither disjunct is true by default -> **native PG introspector runs**.
- **Live (`TrinoPgCatalogLandmineTest`)** — `pg_catalog.pg_namespace / pg_class / pg_attribute / pg_type`
  all **ABSENT** ("Schema 'pg_catalog' does not exist"); `format_type()`, `current_setting()`,
  `pg_table_is_visible()` **unregistered**; a real PG-style `pg_class |><| pg_namespace` L1 join **FAILS**.
  Meanwhile `tpch.information_schema.{tables,columns}` **works** (the safe surface).

## JDBC metadata truth table (trino-jdbc 483, live)

| Method | Result |
|---|---|
| identity | product `Trino`, version `483`, driver `Trino JDBC Driver 483`, **major 483** |
| getCatalog() | **null** when URL names no catalog; **`memory`** when URL is `/memory/default` (never a live "current") |
| getCatalogs | `jmx, memory, system, tpcds, tpch` — **connectors ARE catalogs** (basis for the multi-catalog tree) |
| getSchemas(tpch) | `information_schema, sf1...sf100000, sf300/3000/30000, tiny`; catalog filter honored |
| getTables | `tpch.tiny.orders`->TABLE; wire-created `memory.default.t`->visible; catalog+schema filters narrow |
| getColumns TYPE_NAME | `bigint`(-5), `varchar`(12), **`array(varchar)`**(2003), **`map(varchar, varchar)`**(2000), **`row("a" bigint, "b" varchar)`**(2000) — full Trino spellings survive |
| getColumns NULLABLE | default cols nullable; **`NOT NULL` reported** by the memory connector (NULLABLE=0 / IS_NULLABLE=`NO`) |
| getPrimaryKeys | **EMPTY** — Trino models no primary keys (engine design, not a driver gap) |
| getImportedKeys | **EMPTY** — Trino models no foreign keys |
| getTableTypes | `TABLE, VIEW` |
| getTypeInfo | 50 rows (grid type mapping is backed) |

**GAP (by engine design):** no PK/FK, and complex types (`array/map/row`) surface as opaque type
strings (no expandable sub-fields via JDBC). Both are properties of the engine/JDBC surface, not
fixable by an introspector swap.

## DECISION MEMO — introspector routing (the landmine remedy)

**A — Accept / do nothing.** The PG native introspector runs against Trino; its `pg_catalog` L1
queries throw -> introspection errors at attach, empty/broken tree. **Reject.**

**B — Default `LocalDataSource.setUseJdbcIntrospector(true)` for TRINO_BRIKK sources.** This is a
**PUBLIC** API (`useJdbcIntrospector()`/`setUseJdbcIntrospector()` on `LocalDataSource`) — setting it
forces the generic `JdbcIntrospector` regardless of the version gate. **No internal API.** Cost: needs
a public seam to apply the default on data-source creation (a `DataSourceManager` listener / creation
hook); it is a per-source flag, so an un-hooked source reverts to the landmine. Worth a short spike —
if the seam exists this is the cleanest fix.

**C — Register a non-native `DBIntrospector.Factory` for TRINO_BRIKK.** `forDbms(TRINO_BRIKK)` would
then resolve to OUR factory *before* the POSTGRES fallback; with `isNative()=false` the selection
rule yields `supportedByNativeIntrospector=false` -> generic `JdbcIntrospector` every time,
independent of any per-source flag. Robust and self-contained. **Cost: exposes the
`@ApiStatus.Internal com.intellij.database.introspection.DBIntrospector.Factory` API** — the exact
internal dependency our **doris** and **duckdb** siblings already carry under **IJPL-249765**. A
lightweight stub factory suffices to route to generic; a full `information_schema`-based native Trino
introspector is the richer future (constraint-free tree, per-catalog schemas).

**D — Version-spoof (report major < 9).** The version comes from the driver's `DatabaseMetaData`
out-of-process; doris's "version-gating dead end" showed a plugin cannot rewrite it. **Reject.**

**Recommendation: ship C (guaranteed), spike B (cleaner if feasible).** C deterministically defeats
the landmine regardless of per-source settings and is well-trodden (doris/duckdb + IJPL-249765 cover);
prefer it for the seed's first attach-safe release. Evaluate B in parallel — a public-API default
would avoid the internal dependency entirely. Either way the generic `JdbcIntrospector`, once
selected, reads the strong metadata surface proven above (catalogs, schemas, tables/views, columns
with full Trino type spellings, NOT NULL) — a genuinely useful tree at zero engine risk.

**Residual (headless tests can't prove):** confirm in a live IDE that the generic `JdbcIntrospector`
populates the `PgMetaModel` facade (`TrinoModelFacade`) without a ClassCast (the doris model-family
lesson). PgMetaModel is a superset of the base JDBC model, so it should hold — but it is the one thing
outside this battery's reach.

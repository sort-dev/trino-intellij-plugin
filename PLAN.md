# trino-intellij — the coverage plan

Seeded 2026-07-23, same day duckdb-intellij 0.1.0 shipped. The doris/duckdb playbook, third
surface — with the best asset lineup yet: **Trino publishes its own parser**
(io.trino:trino-parser, versioned with the engine — currently **483**), so the engine-authority
role that doris had to carve out of fe jars and duckdb borrowed from driver jars is a clean
bundled Maven dependency here. brikk-sql already carries Trino function knowledge;
dev.brikk.ducklake:ducklake-test-corpus-replay ships a TrinoReplayEngine adapter; the user runs
prod Trino. No first-party JetBrains Trino/Presto/Athena dialect exists — void-filling, not
compete-with-bundled.

Each stage has an exit criterion; census methodology and cross-cutting rules are the duckdb
PLAN's, unchanged (measured coverage, no silent regressions, verifier both gens, design-first,
publish only on the user's word).

## Stage 0 — Seed ✅ (this commit)

Dbms `TRINO_BRIKK` alongside stock (none exists), PG substrate (ANSI-adjacent surface: standard
strings/identifiers, `||`, LATERAL, GROUPING SETS, FETCH FIRST), TrinoLexer pass-through seam,
seed lenient heads (USE/SHOW/DESCRIBE/SET SESSION/RESET SESSION/DEALLOCATE PREPARE/CREATE|DROP
CATALOG), suppression baseline, ModelFacade via PgModelAccess shim, trino-parser 483 embedded +
authority-proven by test, official trino-jdbc 483 pinned artifact + sessionUser-aware templates,
boot/facts/authority tests.

## Stage 1 — Census + boundaries

Harvest a census from Trino's own repo at the 483 tag (`testing/trino-product-tests` SQL,
docs SQL snippets, curated corpus) **graded by the bundled trino-parser** (exact authority, no
server): scoreboard = our-substrate-parse vs trino-parser verdict per statement. Grow
TrinoPsiParser dispatch + TrinoLexer bridges until the census is green (duckdb bar: 100% of
sampled families, degraded shapes documented). Lambdas (`x -> f(x)` — PG lexes `->` as its JSON
operator), TABLE()/descriptor TVF args, MATCH_RECOGNIZE are the expected token-layer cases.

## Stage 2 — The validator (engine-exact errors, zero infrastructure)

ExternalAnnotator driving the bundled trino-parser: exact ParsingException line/col → squiggles
with Trino's own messages, working with NO data source configured (better than duckdb — no
driver/engine needed). Optional live tier later: `EXPLAIN (TYPE VALIDATE)` over the data source
for semantic validation (TrinoReplayEngine seam).

## Stage 3 — Completion + function catalog

Bundled snapshot harvested from a live Trino 483 (`SHOW FUNCTIONS`: name, kind → icons
scalar/aggregate/window/table, return/arg types) + keywords from trino-parser's reserved list;
kind icons per the doris/duckdb mapping. Then the duckdb Stage-4b port: live per-connection
catalog (connectors change the function set), interceptor harvest + manual refresh + observer.

## Stage 4 — Introspection + tree truth

Truth battery first (duckdb lesson: measure before building): JDBC metadata over trino-jdbc vs a
live 483 container — catalogs (= connectors!), schemas, tables, columns, types; which
introspector actually runs (expect generic JDBC via the PG-factory version-gate rejection);
cancel truth (trino-jdbc implements cancel via the REST query API — verify); grids for
SHOW/DESCRIBE/EXPLAIN. Decision memo before any custom introspector.

**LANDMINE found + fixed:** the expectation above was wrong — Trino reports major **483**, which
*clears* the PG factory's `>= 9` gate, so the native PG introspector was selected and died on
`pg_catalog` at attach; fixed by `TrinoIntrospectorGate` (dbms-exact `<introspector>` vetoing all
versions → generic JDBC introspector always; Path B `setUseJdbcIntrospector` rejected: the flag is
`legacy-introspector`, force-reset by the platform after every IDE build change).

## Stage 5 — Console UX + auth polish

sessionUser/no-auth-without-SSL matrix proven against the container (the connect-dialog rule:
no-TLS template maps User field → sessionUser=, Password MUST stay empty — trino-jdbc hard-errors
on password without SSL). DatabaseAuthProvider "session user / token" panel = the eventual
first-class dialog fix (shared backlog item with duckdb's quack token panel).

## Stage 6 — Ship + ecosystem

Marketplace face (name/icon = user's call — Trino logo is TSF trademark, placeholder until
decided), README measured-coverage framing, release-* CI train (already in repo), brikk-sql
pipes surface when its Trino face is ready.

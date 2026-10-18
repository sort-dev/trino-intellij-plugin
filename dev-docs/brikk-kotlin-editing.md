# Brikk Kotlin editing

Resolved `Sql.trino` calls inside `@BrikkSql` functions automatically enable a
file-local editing profile in IDEA with Kotlin K2. The project PIPE setting stays
independent. For example:

```kotlin
@BrikkSql
fun selected(src: Rel<Events>, tenant: String) = Sql.trino("""
    FROM $src()
    |> WHERE tenant_id = $tenant
    |> SELECT id, tenant_id
""".trimIndent())
```

The SQL inspection view uses `src()` and `:tenant`, while Kotlin retains the
authored references for rename and navigation. Declared and compiler-generated
input contracts supply scoped column completion, resolution and Kotlin navigation.
Aliases, joins and projection visibility are statement-local. Partial inputs stay
partial; Kotlin type families do not invent exact SQL types.

Inspection templates cannot be executed or previewed as compiled output. Execute
the compiled/composed relation with its binding map. Complex interpolation is
reported on the authored Kotlin; pending analysis withdraws stale classifications.

Shared SQL/IDE mechanics live in `brikk-sql-intellij-support`. Kotlin recognition
and contracts come from `brikk-engine-kotlin-intellij-support`. See
[the verification report](REVIEW-trino-pipes-brikk.md) for earlier integration checks.

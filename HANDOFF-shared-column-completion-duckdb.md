# Shared rich column completion

Related consumers: [Doris adoption](HANDOFF-shared-column-completion-doris.md).
SQL Transpiler currently registers no column-completion contributor and needs
no presentation migration for this change.

Trino is adopting `SqlColumnCompletion` from SQL IntelliJ support 0.1.1, using
DuckDB's current presentation as the reference. Publication/consumer evidence
will be recorded in the Trino verification report when the update completes.

Source: `brikk-sql-intellij-support/src/dev.brikk.house.sql.intellij/SqlColumnCompletion.kt`
in `/home/jayson/DEV/brikk/brikk-sql-worktrees/intellij-support`.

The formatter accepts strings and nullable Boolean facts. It adds no dependency
on the Kotlin helper or SQL engine. The result already carries priority 120.
It preserves distinct lookup identities for identical column names under
different aliases.

## DuckDB adoption

Use `brikk-sql-intellij-support:0.1.1` and import
`dev.brikk.house.sql.intellij.SqlColumnCompletion`. In
`BrikkSqlCompletionContributor`, keep scope selection and the existing
`distinctBy { relation.alias to column.name }` logic, then render each entry with:

```kotlin
val nullable = when (column.nullability) {
    ColumnNullability.NON_NULL -> false
    ColumnNullability.NULLABLE -> true
    ColumnNullability.UNKNOWN -> null
}
val origin = if (column.navigation?.kind == NavigationKind.RELATION_FALLBACK)
    "relation contract" else "brikk column"
result.caseInsensitive().addElement(SqlColumnCompletion.create(
    column.name,
    column.kotlinClassId?.substringAfterLast('.'),
    nullable,
    relation.alias,
    origin,
))
```

This replaces the local type/nullability/tail/priority formatting. Remove the
unused `LookupElementBuilder` import and update the helper coordinate in bundled
notices. The package audit already derives the helper JAR name from its version.

Expected entries:

- `payload` with tail `brikk column (src)` and type `String?`.
- `event_at` with tail `brikk column (src)` and type `Instant`.
- Generated-column fallback with tail `relation contract (src)`.
- Unknown nullability keeps the explicit `(nullability unknown)` hint.
- A join's `id` from aliases `a` and `b` remains two distinguishable suggestions.

The active DuckDB label edit and dogfood artifact stay with the DuckDB agent.
This handoff is the small adoption step for the shared renderer, not a new scope
resolver or a change to insertion text.

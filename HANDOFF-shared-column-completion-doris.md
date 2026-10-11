# Doris: shared rich column completion

Use `dev.brikk.house:brikk-sql-intellij-support:0.1.1` for the shared
`dev.brikk.house.sql.intellij.SqlColumnCompletion` formatter. Trino is adopting
the same rendering as DuckDB. Version 0.1.1 is published on Central and bundled
in Trino 0.3.4. Release and consumer evidence is in `REVIEW-trino-pipes-brikk.md`.

Source is in `brikk-sql-intellij-support/src/dev.brikk.house.sql.intellij/SqlColumnCompletion.kt`.
The helper accepts copied strings and nullable Boolean facts; no Kotlin IDE or
SQL engine dependency is added. Existing helper execution classes are unchanged.

## Map Doris display facts

The current `BrikkColumnDisplay` already contains the required facts:

```kotlin
val nullable = when (display.nullability) {
    ColumnNullability.NON_NULL -> false
    ColumnNullability.NULLABLE -> true
    ColumnNullability.UNKNOWN -> null
}
val label = when {
    display.relationFallback -> "relation contract"
    display.origin != null -> "brikk column"
    else -> "PIPE column"
}
val shared = SqlColumnCompletion.create(
    display.name,
    display.kotlinClassId?.substringAfterLast('.'),
    nullable,
    display.origin,
    label,
)
```

This produces `brikk column (src)`, nullable type suffixes such as `String?`,
explicit unknown-type/nullability hints, and `relation contract` for generated
navigation fallbacks. Preserve Doris's neutral `PIPE column` label for computed
expressions without proven Kotlin provenance. The default priority is 120.

## Two adoption paths

1. **Owned PIPE suggestions** in `DorisCompletionContributor`: use the shared
   lookup instead of duplicating the type/tail/priority formatting. Retain the
   field icon with a lookup decorator if needed. The helper differentiates equal
   column names from different source aliases through their lookup identities.
2. **Native SQL decoration** in `BrikkColumnCompletion`: retain the original
   `completion.lookupElement`, prefix matcher, sorter, insertion handler, icon
   and navigation target. Replacing it with a new string lookup would discard
   platform behavior. To share formatting with the current API, render `shared`
   into a temporary `LookupElementPresentation` and copy only its `typeText` and
   `tailText` from your existing decorator's `renderElement` method. A future
   text-only/decorator convenience can simplify this without changing the policy.

`BrikkPipeColumnDisplay` should continue proving passthrough lineage and deciding
which facts are known. This helper renders those facts; it does not infer types,
invent source aliases or turn computed expressions into Kotlin columns.

## Consumer checks

- Nullable and non-null input properties display their type and relation alias.
- Unknown and computed types remain explicitly unknown.
- Generated columns retain labelled relation-contract fallback navigation.
- Equal names from two aliases remain distinguishable.
- Native insertion, qualification, navigation and inspection behavior survive
  the presentation decorator.

Update the helper version in build configuration and bundled notices, then run
the existing completion/lineage tests and package checks in the Doris agent's
validation build directory. The active Doris source and dogfood ZIP are owned
by that agent.

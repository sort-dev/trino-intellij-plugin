package dev.sort.trino.sql

import com.intellij.database.Dbms
import com.intellij.sql.dialects.base.TokensHelper
import com.intellij.sql.dialects.functions.SqlFunctionsUtil
import com.intellij.sql.dialects.postgres.PgDialect
import com.intellij.sql.dialects.postgres.PgDialectBase
import com.intellij.sql.dialects.postgres.PgTokens
import dev.sort.trino.TrinoDbms

/**
 * The "Trino (Brikk)" SQL dialect, based on Postgres ([PgDialectBase]).
 *
 * Trino speaks ANSI-standard SQL with PG-adjacent surface: standard `'...'` strings and `"..."`
 * identifiers, `||` concat, ANSI joins + LATERAL, GROUPING SETS/CUBE/ROLLUP, FETCH FIRST,
 * ARRAY[] constructors, EXISTS/IN subqueries — the PG substrate mis-parses the LEAST (the same
 * evidence-based substrate call doris made with MySQL and duckdb made with PG). Trino-only
 * syntax the PG grammar can't parse (lambdas `x -> f(x)`, `TABLE()` TVF arguments,
 * MATCH_RECOGNIZE, catalog-qualified 3-part DDL, SHOW/USE/session statements, SQL routines) is
 * measured by the census scoreboard and handled with the proven playbook: lenient statement
 * boundaries in [TrinoPsiParser], lexer masking only where structurally unavoidable, and an
 * authoritative validator — Trino's OWN parser (io.trino:trino-parser), bundled and versioned.
 *
 * The explicit [TokensHelper] mirrors the doris lesson: the default createTokensHelper(Class)
 * resolves `functions.xml` relative to THIS class's package (which has none) and silently yields
 * an EMPTY builtin-function map, breaking special-form builtins (CAST(x AS t), EXTRACT, ...).
 * Loading PgDialect's own definitions keeps us in sync with the platform.
 */
class TrinoSqlDialect private constructor() : PgDialectBase("TrinoBrikk") {
    override fun getDbms(): Dbms = TrinoDbms.TRINO_BRIKK

    // PgDialectBase leaves these abstract (the concrete per-version dialects fill them in).
    // Delegate to modern PG so operator/system-variable knowledge stays in sync with the platform.
    override fun isOperatorSupported(op: com.intellij.psi.tree.IElementType): Boolean =
        PgDialect.INSTANCE.isOperatorSupported(op)

    override fun getSystemVariables(): MutableSet<String> = PgDialect.INSTANCE.systemVariables

    override fun createTokensHelper(): TokensHelper =
        TokensHelper(
            PgTokens::class.java,
            SqlFunctionsUtil.loadFunctionDefinition(PgDialect.INSTANCE)
        )

    companion object {
        @JvmField
        val INSTANCE = TrinoSqlDialect()
    }
}

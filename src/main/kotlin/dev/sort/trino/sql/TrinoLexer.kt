package dev.sort.trino.sql

import com.intellij.lexer.LookAheadLexer
import com.intellij.sql.dialects.postgres.PgLexer

/**
 * Token-layer seam over the PG lexer (the duckdb DuckdbLexer position, currently pass-through).
 *
 * The census scoreboard decides which Trino constructs need token bridging; candidates from the
 * duckdb experience: lambda heads (`x -> f(x)` — PG lexes `->` as its JSON operator, structurally
 * quiet but semantically wrong tree), `TABLE(...)`/descriptor arguments in TVF calls,
 * MATCH_RECOGNIZE pattern bodies. Rules land here ONLY when the scoreboard proves the parser
 * alone can't hold the boundary — masking is a last resort, and every mask gets a recolor
 * annotator entry so painted-over spans keep real word colors.
 */
class TrinoLexer : LookAheadLexer(PgLexer())

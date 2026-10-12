package dev.sort.trino.sql

import dev.brikk.house.sql.intellij.editor.KeywordOverlayHighlighterFactory

class TrinoSyntaxHighlighterFactory : KeywordOverlayHighlighterFactory(TrinoSqlDialect, TRINO_KEYWORDS)

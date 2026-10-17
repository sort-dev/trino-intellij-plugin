package dev.sort.trino.sql

import dev.brikk.house.sql.intellij.editor.SqlDialectQuoteHandler

class TrinoQuoteHandler : SqlDialectQuoteHandler(EnterInString.Newline)

package dev.sort.trino.sql

import dev.brikk.house.sql.intellij.editor.MaskedSpanRecolorAnnotator

class TrinoMaskedSpanRecolorAnnotator : MaskedSpanRecolorAnnotator(TrinoSqlDialect, "trino", TRINO_KEYWORDS)

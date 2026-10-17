package dev.sort.trino.catalog

import dev.brikk.house.sql.intellij.catalog.DialectDataSourceResolver
import dev.sort.trino.TrinoDbms

internal val trinoDataSourceResolver = DialectDataSourceResolver(TrinoDbms.TRINO_BRIKK)

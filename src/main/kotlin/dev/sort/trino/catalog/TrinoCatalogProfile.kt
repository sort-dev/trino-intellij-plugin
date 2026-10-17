package dev.sort.trino.catalog

import com.intellij.database.model.ObjectKind
import dev.brikk.house.sql.intellij.catalog.CatalogProfile
import dev.sort.trino.TrinoDbms

object TrinoCatalogProfile : CatalogProfile {
    override val dbms = TrinoDbms.TRINO_BRIKK
    override val productLabel = "Trino"
    override val quoteChars = "\""
    override val levels = listOf(ObjectKind.DATABASE, ObjectKind.SCHEMA)
    override val autoIntrospect = true
    override val dataSources = trinoDataSourceResolver
}

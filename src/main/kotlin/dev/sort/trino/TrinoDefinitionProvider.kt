package dev.sort.trino

import com.intellij.database.model.ObjectKind
import dev.brikk.house.sql.intellij.catalog.ShowCreateDefinitionProvider
import dev.brikk.house.sql.shape.DefinedObject

class TrinoDefinitionProvider : ShowCreateDefinitionProvider(
    "trino",
    mapOf(
        ObjectKind.TABLE to DefinedObject.TABLE,
        ObjectKind.VIEW to DefinedObject.VIEW,
        ObjectKind.MAT_VIEW to DefinedObject.MATERIALIZED_VIEW,
        ObjectKind.SCHEMA to DefinedObject.SCHEMA,
    ),
    listOf(ObjectKind.DATABASE, ObjectKind.SCHEMA),
)

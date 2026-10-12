package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.dialects.base.TypeHelper
import com.intellij.database.dialects.postgres.PgDialect
import com.intellij.database.dialects.postgresbase.PgDialectBase
import com.intellij.database.model.DasObject
import com.intellij.database.util.DdlBuilder
import com.intellij.database.util.SearchPath
import dev.brikk.house.sql.intellij.catalog.CatalogSchemaSearchPath
import dev.brikk.house.sql.intellij.catalog.NamespaceProbe

class TrinoDatabaseDialect : PgDialectBase(TypeHelper.PgTypeHelper()) {
    private val postgres = PgDialect(TrinoDbms.TRINO_BRIKK)
    private val namespace = CatalogSchemaSearchPath(
        quote = '"', useSchema = "USE {catalog}.{schema}",
        catalogProbes = listOf(NamespaceProbe.Column(READ_BACK, "catalog")),
        schemaProbes = listOf(NamespaceProbe.Column(READ_BACK, "schema")), schemaRequired = true,
    )

    override fun getDbms(): Dbms = TrinoDbms.TRINO_BRIKK

    override fun getDisplayName(): String = dbms.displayName

    override fun qualifiedIdentifier(
        builder: DdlBuilder,
        identifier: String,
        definition: DasObject?,
        qualifier: DasObject,
    ): DdlBuilder = postgres.qualifiedIdentifier(builder, identifier, definition, qualifier)

    override fun sqlSetSearchPath(path: SearchPath): String? = namespace.switchSql(path)

    override fun sqlResetSearchPath(): String? = null

    override fun getSearchPathQuery(): String = READ_BACK

    override fun tryToLoadSearchPath(connection: DatabaseConnectionCore): SearchPath? = namespace.readBack(connection)

    private companion object {
        const val READ_BACK = "SELECT current_catalog AS catalog, current_schema AS schema"
    }
}

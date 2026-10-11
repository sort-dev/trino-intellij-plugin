package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.dialects.base.TypeHelper
import com.intellij.database.dialects.postgresbase.PgDialectBase
import com.intellij.database.model.ObjectKind
import com.intellij.database.model.DasObject
import com.intellij.database.util.DbImplUtilCore
import com.intellij.database.util.DdlBuilder
import com.intellij.database.util.ObjectPath
import com.intellij.database.util.SearchPath

class TrinoDatabaseDialect : PgDialectBase(TypeHelper.PgTypeHelper()) {
    override fun getDbms(): Dbms = TrinoDbms.TRINO_BRIKK

    override fun getDisplayName(): String = dbms.displayName

    override fun qualifiedIdentifier(
        builder: DdlBuilder,
        identifier: String,
        definition: DasObject?,
        qualifier: DasObject,
    ): DdlBuilder = com.intellij.database.dialects.postgres.PgDialect(dbms)
        .qualifiedIdentifier(builder, identifier, definition, qualifier)

    override fun sqlSetSearchPath(path: SearchPath): String? {
        val schema = path.current
        if (schema.kind != ObjectKind.SCHEMA) return null
        val catalog: ObjectPath = schema.findParent(ObjectKind.DATABASE, true) ?: return null
        return DdlBuilder().plain("USE ").quoteIdentifiers(true)
            .identifier(catalog.name).symbol(".").identifier(schema.name).statement
    }

    override fun sqlResetSearchPath(): String? = null

    override fun getSearchPathQuery(): String = "SELECT current_catalog AS catalog, current_schema AS schema"

    override fun tryToLoadSearchPath(connection: DatabaseConnectionCore): SearchPath? {
        val row = DbImplUtilCore.resultRowAsMap(connection, searchPathQuery)
        val catalog = row["catalog"] as? String ?: return null
        val schema = row["schema"] as? String ?: return null
        return SearchPath.of(ObjectPath.create(catalog, ObjectKind.DATABASE).append(schema, ObjectKind.SCHEMA))
    }
}

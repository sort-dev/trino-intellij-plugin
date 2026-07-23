package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.database.dialects.postgres.model.PgMetaModel
import com.intellij.database.model.ModelFacade
import com.intellij.database.model.ModelHelper
import com.intellij.database.model.meta.BasicMetaModel

/**
 * Model shape for Trino (Brikk) data sources: the public PG meta-model + helper, matching the
 * dialect substrate and the `extensionFallback → POSTGRES` line in plugin.xml.
 *
 * This class is the ClassCast gate (doris lesson, re-proven on duckdb): the model facade, the
 * parsing dialect's metadata family, and the introspector must AGREE on a model family, or
 * attaching a data source dies with `...ImplModel$Root cannot be cast ...`. PG is also the right
 * long-term family here: Trino is multi-catalog by construction (every connector is a catalog),
 * and PG's model carries a database level — the catalogs-in-tree work builds on this choice.
 * (duckdb truth battery: the PG introspector's version gate rejects non-PG engines, so the
 * generic JDBC introspector runs — expected to hold for Trino too; truth lane verifies.)
 */
class TrinoModelFacade(dbms: Dbms) : ModelFacade(dbms) {
    override fun getMetaModel(): BasicMetaModel<*> = PgMetaModel.MODEL

    // Via the Java shim: PgModelHelper is Kotlin-internal in metadata (public bytecode).
    override fun getModelHelper(): ModelHelper = PgModelAccess.pgModelHelper()
}

package dev.sort.trino

import com.intellij.database.Dbms
import com.intellij.openapi.util.IconLoader

/**
 * The "Trino (Brikk)" dbms — minted ALONGSIDE anything the platform ships, never overriding:
 * stock/generic Trino data sources keep behaving stock, and a future first-party Trino dialect
 * from JetBrains cannot collide with us. Same coexistence strategy as the Doris and DuckDB
 * plugins (StarRocks lineage). Today DataGrip has NO Trino dialect at all — Trino/Presto/Athena
 * users get Generic SQL — so this plugin fills a void rather than shadowing built-ins.
 */
object TrinoDbms {
    // Official Trino mark (trino.io/assets/images/trino-logo, used unmodified). "Trino" and the
    // Trino logo are trademarks of the Trino Software Foundation; used per user decision to identify
    // the engine this dialect supports (see trino.io/legal).
    private val icon = IconLoader.getIcon("/icons/trino.svg", TrinoDbms::class.java)

    @JvmField
    val TRINO_BRIKK: Dbms = Dbms.create(
        "TRINO_BRIKK",
        // User-facing dialect label (the SQL-dialect picker); "sort.dev" is the vendor, no
        // engine-backing claim. The dbms id stays TRINO_BRIKK; the SQL dialect id is TrinoSQL (code + mappings).
        "Trino (sort.dev)",
        { icon },
        Dbms.defaultPattern("trino")
    )
}

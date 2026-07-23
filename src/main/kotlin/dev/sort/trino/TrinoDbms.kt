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
    // Placeholder mark (neutral, ours). The Trino name/logo are trademarks of the Trino Software
    // Foundation — do NOT ship their logo without an explicit user decision + policy check.
    private val icon = IconLoader.getIcon("/icons/trino-brikk.svg", TrinoDbms::class.java)

    @JvmField
    val TRINO_BRIKK: Dbms = Dbms.create(
        "TRINO_BRIKK",
        "Trino (Brikk)",
        { icon },
        Dbms.defaultPattern("trino")
    )
}

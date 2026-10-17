package dev.sort.trino.sql

import com.intellij.database.Dbms
import dev.brikk.house.sql.intellij.dialect.PgFamilyDialect
import dev.sort.trino.TrinoDbms

object TrinoSqlDialect : PgFamilyDialect("TrinoSQL") {
    override fun getDbms(): Dbms = TrinoDbms.TRINO_BRIKK
}

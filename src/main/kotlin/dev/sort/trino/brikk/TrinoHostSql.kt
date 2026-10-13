package dev.sort.trino.brikk

import dev.brikk.house.sql.intellij.host.HostSqlChannel
import dev.brikk.house.sql.intellij.host.HostSqlPlaceholderErrorFilter

object TrinoHostSql : HostSqlChannel("dev.sort.trino.brikk")

class TrinoHostPlaceholderErrorFilter : HostSqlPlaceholderErrorFilter(TrinoHostSql)

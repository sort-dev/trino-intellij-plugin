package dev.sort.trino

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.plan.ExplainPlanProvider
import com.intellij.database.plan.PlanModel
import com.intellij.database.util.Version
import com.intellij.util.Consumer

class TrinoExplainPlanProvider : ExplainPlanProvider(TrinoDbms.TRINO_BRIKK) {
    override fun isSupported(version: Version, analyze: Boolean): Boolean = false

    override fun isRawSupported(version: Version, analyze: Boolean): Boolean = false

    override fun createExplainRequest(
        owner: DataRequest.OwnerEx,
        consumer: Consumer<in PlanModel>,
        dataSource: LocalDataSource,
        statement: String,
        analyze: Boolean,
    ): DataRequest.RawRequest = throw UnsupportedOperationException()

    override fun createRawExplainTask(dataSource: LocalDataSource, analyze: Boolean): RawExplainTask =
        throw UnsupportedOperationException()
}

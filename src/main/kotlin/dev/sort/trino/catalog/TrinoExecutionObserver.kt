package dev.sort.trino.catalog

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.brikk.house.sql.intellij.catalog.ExecutionObserver
import dev.brikk.house.sql.intellij.catalog.ExecutionObserverProfile
import dev.brikk.house.sql.intellij.catalog.ExecutionObserverStartup
import dev.brikk.house.sql.intellij.catalog.StatementHeadDetector
import dev.brikk.house.sql.intellij.catalog.StatementRules
import dev.sort.trino.TrinoDbms
import kotlinx.coroutines.CoroutineScope

object TrinoExecutionObserverProfile : ExecutionObserverProfile {
    override val dbms = TrinoDbms.TRINO_BRIKK
    override val statements = StatementHeadDetector(StatementRules.TRINO, "trino")
    override val autoIntrospect = TrinoAutoIntrospectService::class.java
}

@Service(Service.Level.PROJECT)
class TrinoExecutionObserver(project: Project, scope: CoroutineScope) : ExecutionObserver(project, scope) {
    override val profile = TrinoExecutionObserverProfile
}

class TrinoExecutionObserverStartup : ExecutionObserverStartup(TrinoExecutionObserver::class.java)

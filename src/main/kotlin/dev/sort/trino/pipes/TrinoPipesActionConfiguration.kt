package dev.sort.trino.pipes

import dev.brikk.house.sql.intellij.CooperativeExecuteConfiguration
import dev.brikk.house.sql.intellij.CooperativeExecutePromoter
import dev.brikk.house.sql.intellij.pipe.PipeExecuteInterceptor

class TrinoPipesActionConfiguration : CooperativeExecuteConfiguration() {
    override val interceptor = PipeExecuteInterceptor(TrinoPipeDialect)
}

class TrinoPipesActionPromoter : CooperativeExecutePromoter()

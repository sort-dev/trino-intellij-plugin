package dev.sort.trino.pipes

import dev.brikk.house.sql.intellij.pipe.PipeStageCompletionContributor
import dev.brikk.house.sql.intellij.pipe.PipeStageKeywordAnnotator

class TrinoPipesCompletionContributor : PipeStageCompletionContributor(TrinoPipeDialect)
class TrinoPipeStageKeywordAnnotator : PipeStageKeywordAnnotator(TrinoPipeDialect)

package dev.sort.trino.pipes

import dev.brikk.house.sql.intellij.pipe.PipePreviewAction
import dev.brikk.house.sql.intellij.pipe.PipePreviewIntention
import dev.brikk.house.sql.intellij.pipe.PipeRunToStageAction
import dev.brikk.house.sql.intellij.pipe.PipeRunToStageIntention

class PreviewPipeSqlIntention : PipePreviewIntention(TrinoPipeDialect)
class RunPipesToStageIntention : PipeRunToStageIntention(TrinoPipeDialect)
class PreviewPipeSqlAction : PipePreviewAction(TrinoPipeDialect)
class RunPipesToStageAction : PipeRunToStageAction(TrinoPipeDialect)

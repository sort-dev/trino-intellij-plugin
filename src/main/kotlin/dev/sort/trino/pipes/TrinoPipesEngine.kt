package dev.sort.trino.pipes

import dev.brikk.house.sql.ast.PipeQuery
import dev.brikk.house.sql.generator.UnsupportedError
import dev.brikk.house.sql.parser.ParseError
import dev.brikk.house.sql.parser.TokenError
import dev.brikk.house.sql.parser.PipeStageSplitter
import dev.brikk.house.sql.shape.*
import dev.sort.trino.validate.TrinoStatementValidator

object TrinoPipesEngine {
    sealed interface Transpile {
        data class Ok(val sql: String, val result: TranspileResult, val validationError: Err? = null) : Transpile {
            val executionError: Err? get() = validationError ?: result.unsupportedMessages.takeIf { it.isNotEmpty() }?.let {
                Err(null, null, "Unsupported or lossy PIPE translation:\n" + it.joinToString("\n"))
            }
        }
        data class Err(val line: Int?, val col: Int?, val message: String) : Transpile
        data object NotPipe : Transpile
    }
    fun transpile(text: String, beforeSubstitution: Boolean = true, allowFirstQueryStage: Boolean = false): Transpile {
        val chunks = TrinoPipes.chunks(text)
        if (chunks.none { it.hasPipeOperator } && !allowFirstQueryStage) return Transpile.NotPipe
        chunks.firstOrNull { it.boundaryError != null }?.let { return Transpile.Err(null, null, it.boundaryError!!) }
        if (chunks.count { it.hasSql } != 1) return Transpile.Err(null, null, "Select exactly one complete PIPE statement.")
        return try {
            val fragment = SqlFragment(text.trim().removeSuffix(";"), "trino")
            if (!fragment.ast.findAll<PipeQuery>().any() && !(allowFirstQueryStage && fragment.ast is dev.brikk.house.sql.ast.Select))
                return Transpile.Err(null, null, "The statement is not a PIPE query.")
            val result = fragment.toExecutable("trino", pretty = true)
            if (result.isRawPassthroughStatement || TrinoPipes.containsPipeOperator(result.sql))
                return Transpile.Err(null, null, "Translation left unlowered SQL; execution blocked.")
            val validation = if (result.unsupportedMessages.isEmpty()) {
                val sql = if (beforeSubstitution) maskNamedParameters(result.sql) else result.sql
                TrinoStatementValidator.parse(sql)?.let {
                    Transpile.Err(null, null, "Generated SQL failed Trino validation: ${it.message}")
                }
            } else null
            Transpile.Ok(result.sql, result, validation)
        } catch (e: ParseError) {
            val first = e.errors.firstOrNull()
            Transpile.Err(first?.line, first?.col, first?.description ?: e.message ?: "Invalid PIPE query")
        } catch (e: UnsupportedError) {
            Transpile.Err(null, null, e.message ?: "Unsupported PIPE query")
        } catch (e: TokenError) {
            Transpile.Err(null, null, e.message ?: "Invalid SQL token")
        } catch (e: ShapeError) {
            Transpile.Err(null, null, e.message ?: "Invalid SQL shape")
        }
    }

    private fun maskNamedParameters(sql: String): String = buildString {
        append(sql)
        for (parameter in parseParameterOccurrences(sql, "trino")) {
            if (parameter.style != ParamStyle.NAMED_COLON) continue
            setCharAt(parameter.range.start, '0')
            for (i in parameter.range.start + 1 until parameter.range.end) setCharAt(i, ' ')
        }
    }

    data class StagePrefix(val text: String, val stage: Int, val total: Int)
    fun stagePrefixAt(text: String, offset: Int): StagePrefix? = runPipeCatching {
        val split = PipeStageSplitter.split(text, "trino")
        // A caret inside a nested pipeline must not silently run its later stages as
        // part of the outer FROM stage. This command explicitly owns top-level stages.
        data class Frame(val start: Int, var pipe: Boolean = false)
        val stack = mutableListOf<Frame>()
        for (token in split.tokens) when (token.tokenType) {
            dev.brikk.house.sql.parser.TokenType.L_PAREN,
            dev.brikk.house.sql.parser.TokenType.L_BRACKET,
            dev.brikk.house.sql.parser.TokenType.L_BRACE -> stack.add(Frame(token.start))
            dev.brikk.house.sql.parser.TokenType.PIPE_GT -> stack.lastOrNull()?.pipe = true
            dev.brikk.house.sql.parser.TokenType.R_PAREN,
            dev.brikk.house.sql.parser.TokenType.R_BRACKET,
            dev.brikk.house.sql.parser.TokenType.R_BRACE -> if (stack.isNotEmpty()) {
                val frame = stack.removeAt(stack.lastIndex)
                if (frame.pipe && offset in frame.start..token.end) return null
            }
            else -> {}
        }
        val stages = split.stages
        val index = stages.indexOfLast { offset >= it.start }
        if (index < 0 || stages.size < 2) return null
        StagePrefix(text.substring(0, stages[index].endInclusive + 1), index + 1, stages.size)
    }.getOrNull()
}

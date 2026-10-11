package dev.sort.trino.brikk.kotlin

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.*
import com.intellij.lang.annotation.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import dev.brikk.house.intellij.*
import dev.sort.trino.brikk.BrikkTrinoProfile
import dev.sort.trino.sql.TrinoSqlDialect
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class BrikkTrinoInjectionContributor : LanguageInjectionContributor {
    override fun getInjection(context: PsiElement): Injection? {
        val result = BrikkKotlinContext.inspect(context, includeShapes = false) as? ContextResult.Available ?: return null
        if (result.context.dialect != BrikkDialect.TRINO) return null
        return object : Injection {
            override fun getInjectedLanguageId() = "TrinoSQL"
            override fun getInjectedLanguage() = TrinoSqlDialect.INSTANCE
            override fun getPrefix() = "-- dialect: trino\n"
            override fun getSuffix() = ""
            override fun getSupportId() = "kotlin"
        }
    }
}

/** Public injection API, with decoded character origins retained through trimming. */
class BrikkTrinoInjectionPerformer : LanguageInjectionPerformer {
    override fun isPrimary() = true
    override fun performInjection(registrar: MultiHostRegistrar, injection: Injection, context: PsiElement): Boolean {
        val host = context as? KtStringTemplateExpression ?: return false
        if (injection.injectedLanguageId != "TrinoSQL") return false
        val result = BrikkKotlinContext.inspect(host, includeShapes = false)
        if (result is ContextResult.Unavailable) {
            // Withdraw stale ranges; defer rather than letting native injection invent column names.
            host.putUserData(BrikkTrinoProfile.KEY, BrikkTrinoProfile(emptyList(), emptyList(), listOf(result.reason)))
            host.putUserData(BrikkTrinoProfile.RELATIONS, null)
            retry(host)
            return true
        }
        val shared = (result as? ContextResult.Available)?.context
        if (shared?.dialect != BrikkDialect.TRINO) {
            host.putUserData(BrikkTrinoProfile.KEY, null)
            host.putUserData(BrikkTrinoProfile.RELATIONS, null)
            return false
        }
        val profile = BrikkTrinoProfile(shared.binds, shared.slots, shared.entries.mapNotNull { it.problem })
        host.putUserData(BrikkTrinoProfile.KEY, profile)
        val pointer = SmartPointerManager.getInstance(host.project).createSmartPsiElementPointer(host)
        host.putUserData(BrikkTrinoProfile.RELATIONS) {
            val current = pointer.element?.let { BrikkKotlinContext.inspect(it, includeShapes = true) } as? ContextResult.Available
            if (current?.context?.dialect == BrikkDialect.TRINO) current.context.relations else emptyMap()
        }
        val places = places(host, shared)
        registrar.startInjecting(TrinoSqlDialect.INSTANCE)
        registrar.putInjectedFileUserData(BrikkTrinoProfile.KEY, profile)
        registrar.frankensteinInjection(shared.uncertain)
        registrar.makeInspectionsLenient(shared.uncertain)
        places.forEachIndexed { index, place ->
            registrar.addPlace((if (index == 0) injection.prefix else "") + place.prefix,
                if (index == places.lastIndex) injection.suffix else "", host, place.range)
        }
        registrar.doneInjecting()
        return true
    }

    private data class Character(val char: Char, val start: Int, val end: Int, val synthetic: Boolean)
    private data class Place(val range: TextRange, val prefix: String)
    private fun places(host: KtStringTemplateExpression, context: BrikkContext): List<Place> {
        val escaper = host.createLiteralTextEscaper()
        var chars = context.entries.flatMap { entry ->
            val range = TextRange(entry.range.start, entry.range.end)
            entry.replacement?.map { Character(it, range.startOffset, range.startOffset, true) } ?: run {
                val decoded = StringBuilder()
                check(escaper.decode(range, decoded))
                decoded.mapIndexed { i, ch -> Character(ch, escaper.getOffsetInHost(i, range), escaper.getOffsetInHost(i + 1, range), false) }
            }
        }
        for (trim in context.trims) chars = trim(chars, trim == "trimMargin")
        val out = mutableListOf<Place>()
        var synthetic = StringBuilder()
        var anchor = if (host.text.startsWith("\"\"\"")) 3 else 1
        var i = 0
        while (i < chars.size) {
            val ch = chars[i]
            if (ch.synthetic) { synthetic.append(ch.char); anchor = ch.start; i++; continue }
            val start = ch.start
            var end = ch.end
            i++
            while (i < chars.size && !chars[i].synthetic && chars[i].start == end) end = chars[i++].end
            out.add(Place(TextRange(start, end), synthetic.toString()))
            synthetic = StringBuilder()
        }
        if (synthetic.isNotEmpty() || out.isEmpty()) out.add(Place(TextRange(anchor, anchor), synthetic.toString()))
        return out
    }
    private fun trim(chars: List<Character>, margin: Boolean): List<Character> {
        val lines = mutableListOf<List<Character>>()
        var start = 0
        var i = 0
        while (i < chars.size) {
            if (chars[i].char in "\r\n") {
                lines.add(chars.subList(start, i))
                if (chars[i].char == '\r' && chars.getOrNull(i + 1)?.char == '\n') i++
                start = i + 1
            }
            i++
        }
        lines.add(chars.subList(start, chars.size))
        val indent = lines.filter { line -> line.any { !it.char.isWhitespace() } }
            .minOfOrNull { line -> line.takeWhile { it.char.isWhitespace() }.size } ?: 0
        return lines.mapIndexedNotNull { index, line ->
            if ((index == 0 || index == lines.lastIndex) && line.all { it.char.isWhitespace() }) null
            else if (margin) {
                val first = line.indexOfFirst { !it.char.isWhitespace() }
                if (first >= 0 && line[first].char == '|') line.drop(first + 1) else line
            } else line.drop(indent)
        }.flatMapIndexed { index, line ->
            if (index == 0) line else listOf(Character('\n', line.firstOrNull()?.start ?: 0, line.firstOrNull()?.start ?: 0, true)) + line
        }
    }
    private fun retry(host: KtStringTemplateExpression) {
        val revision = PsiModificationTracker.getInstance(host.project).modificationCount
        if (host.getUserData(RETRY) == revision) return
        host.putUserData(RETRY, revision)
        val pointer = SmartPointerManager.getInstance(host.project).createSmartPsiElementPointer(host)
        ApplicationManager.getApplication().invokeLater {
            if (host.project.isDisposed) return@invokeLater
            DumbService.getInstance(host.project).runWhenSmart {
                ReadAction.run<RuntimeException> {
                    val file = pointer.element?.containingFile ?: return@run
                    InjectedLanguageManager.getInstance(file.project).dropFileCaches(file)
                    DaemonCodeAnalyzer.getInstance(file.project).restart(file)
                }
            }
        }
    }
    companion object { private val RETRY = Key.create<Long>("trino.brikk.retry") }
}

class BrikkTrinoHostAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val host = element as? KtStringTemplateExpression ?: return
        val result = BrikkKotlinContext.inspect(host, includeShapes = false) as? ContextResult.Available ?: return
        if (result.context.dialect != BrikkDialect.TRINO) return
        holder.newAnnotation(HighlightSeverity.INFORMATION, "Trino Brikk: PIPE editing enabled; inspection SQL is not executable.").range(host).create()
        result.context.entries.forEach { entry ->
            if (entry.role == EntryRole.PENDING) return@forEach
            entry.problem?.let {
                holder.newAnnotation(HighlightSeverity.ERROR, it)
                    .range(TextRange(entry.range.start, entry.range.end).shiftRight(host.textRange.startOffset)).create()
            }
        }
    }
}

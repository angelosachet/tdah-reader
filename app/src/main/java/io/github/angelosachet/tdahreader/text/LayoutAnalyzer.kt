package io.github.angelosachet.tdahreader.text

import java.text.Normalizer
import kotlin.math.abs

/**
 * Transforma as linhas extraídas do PDF em parágrafos de texto corrido:
 * remove cabeçalhos, rodapés, números de página e notas de rodapé,
 * une linhas quebradas e desfaz a hifenização.
 */
class LayoutAnalyzer(private val dictionary: () -> Set<String> = { emptySet() }) {

    fun analyze(pages: List<PageText>): List<Paragraph> {
        val cleaned = GlyphRepair.repair(pages, dictionary).map { page -> page.copy(lines = page.lines.mapNotNull(::cleanLine)) }
        val bodySize = bodyFontSize(cleaned)
        val content = removeRunningHeaders(cleaned, bodySize).map { removeFootnotes(it, bodySize) }
        val vocabulary = HashSet<String>()
        content.forEach { page ->
            page.lines.forEach { if (it.fontSize < bodySize * 1.15f) vocabulary += Dehyphenator.vocabularyOf(it.text) }
        }
        return ParagraphBuilder(bodySize, content, Dehyphenator(vocabulary)).build()
            .map { if (it.heading) it.copy(text = joinLetterSpaced(it.text, vocabulary)) else it }
    }

    /**
     * Títulos com letras espaçadas ("CA PÍT ULO I") viram pedaços separados na extração.
     * Junta pedaços vizinhos quando o resultado é uma palavra que aparece no livro.
     */
    private fun joinLetterSpaced(text: String, vocabulary: Set<String>): String {
        val tokens = text.split(' ')
        if (tokens.size < 2) return text
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            var best = i
            var j = i + 1
            var joined = tokens[i]
            while (j < tokens.size && j - i < 12) {
                joined += tokens[j]
                val word = joined.trim { !it.isLetter() }.lowercase()
                if (word.length >= 3 && word in vocabulary) best = j
                j++
            }
            val pieces = tokens.subList(i, best + 1)
            val allWords = pieces.all { it.trim { c -> !c.isLetter() }.lowercase() in vocabulary }
            if (best > i && !allWords) {
                out += pieces.joinToString("")
                i = best + 1
            } else {
                out += tokens[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    // ---------------------------------------------------------------- limpeza

    private fun cleanLine(line: TextLine): TextLine? {
        val sb = StringBuilder(line.text.length)
        for ((i, c) in line.text.withIndex()) {
            when {
                c == ' ' || c == ' ' || c == ' ' || c == '\t' -> sb.append(' ')
                c in '​'..'‍' || c == '﻿' -> Unit
                c == Dehyphenator.SOFT_HYPHEN && i != line.text.lastIndex -> Unit
                c.isISOControl() -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        val text = fixDiacritics(sb.toString()).replace(WHITESPACE, " ").trim()
        return if (text.isEmpty()) null else line.copy(text = text)
    }

    /**
     * PDFs gerados pelo LaTeX costumam trazer acentos como caracteres combinantes
     * ("a\u0303" em vez de "ã") e o "í" como acento + "ı" (i sem pingo).
     */
    private fun fixDiacritics(s: String): String {
        if (s.none { it in '\u0300'..'\u036F' || it == 'ı' }) return s
        val sb = StringBuilder(s)
        var i = 0
        while (i < sb.length - 1) {
            if (sb[i] in '\u0300'..'\u036F' && sb[i + 1] == 'ı') {
                val mark = sb[i]
                sb.setCharAt(i, 'i')
                sb.setCharAt(i + 1, mark)
                i++
            } else if (sb[i] == 'ı' && sb[i + 1] in '\u0300'..'\u036F') {
                sb.setCharAt(i, 'i')
            }
            i++
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }

    private fun bodyFontSize(pages: List<PageText>): Float {
        val weights = HashMap<Float, Int>()
        pages.forEach { page ->
            page.lines.forEach { weights.merge(LineAssembler.roundHalf(it.fontSize), it.text.length, Int::plus) }
        }
        return weights.maxByOrNull { it.value }?.key?.takeIf { it > 0f } ?: 10f
    }

    // ---------------------------------------------- cabeçalhos e rodapés

    private fun removeRunningHeaders(pages: List<PageText>, bodySize: Float): List<PageText> {
        // Linhas nas margens superior/inferior da página (a ordem no PDF nem sempre é a visual).
        fun candidates(page: PageText): Set<Int> {
            val n = page.lines.size
            val result = HashSet<Int>()
            if (page.height <= 0f) {
                for (i in 0 until minOf(2, n)) result += i
                for (i in maxOf(0, n - 2) until n) result += i
                return result
            }
            page.lines.forEachIndexed { i, line ->
                val nearEdgeByOrder = i < 2 || i >= n - 2
                val top = line.baseline < page.height * if (nearEdgeByOrder) 0.2f else 0.12f
                val bottom = line.baseline > page.height * if (nearEdgeByOrder) 0.8f else 0.88f
                if (top || bottom) result += i
            }
            return result
        }

        val signatureCount = HashMap<String, Int>()
        pages.forEach { page ->
            candidates(page).map { signature(page.lines[it].text) }.toSet()
                .forEach { signatureCount.merge(it, 1, Int::plus) }
        }

        // Repetido em boa parte das páginas: é cabeçalho, mesmo com fonte grande.
        val frequent = maxOf(5, pages.size / 4)
        return pages.map { page ->
            val remove = candidates(page).filter { i ->
                val line = page.lines[i]
                val sig = signature(line.text)
                when {
                    line.text.none { it.isLetterOrDigit() } -> true
                    PAGE_NUMBER.matches(line.text) -> line.fontSize <= bodySize * 1.15f
                    sig.length >= 3 && (signatureCount[sig] ?: 0) >= frequent -> true
                    sig.length >= 3 && (signatureCount[sig] ?: 0) >= 3 -> line.fontSize <= bodySize * 1.1f
                    else -> false
                }
            }.toSet()
            if (remove.isEmpty()) page else page.copy(lines = page.lines.filterIndexed { i, _ -> i !in remove })
        }
    }

    private fun signature(text: String): String =
        text.lowercase().replace(DIGITS, "").replace(WHITESPACE, " ").trim()

    private fun removeFootnotes(page: PageText, bodySize: Float): PageText {
        val lines = page.lines
        var start = lines.size
        while (start > 0 && lines[start - 1].fontSize <= bodySize * 0.85f) start--
        if (start == lines.size || start == 0) return page
        val first = lines[start]
        val previous = lines[start - 1]
        val looksLikeNote = first.leadingMarker || NOTE_START.containsMatchIn(first.text)
        val separated = first.baseline - previous.baseline > previous.fontSize * 1.3f
        return if (looksLikeNote && separated) page.copy(lines = lines.subList(0, start)) else page
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val DIGITS = Regex("\\d+")
        private val PAGE_NUMBER = Regex(
            """^[\s\-–—.·•|\[\]()]*(p(á|a)g(ina)?\.?\s*)?(\d{1,4}|[ivxlcdm]{1,8})(\s*(de|/|of)\s*\d{1,4})?[\s\-–—.·•|\[\]()]*$""",
            RegexOption.IGNORE_CASE,
        )
        private val NOTE_START = Regex("""^(\d{1,3}|[*∗†‡]+)[.)]?\s*\S""")
    }
}

/** Une as linhas de todas as páginas em parágrafos. */
private class ParagraphBuilder(
    private val bodySize: Float,
    private val pages: List<PageText>,
    private val dehyphenator: Dehyphenator,
) {
    private class Metrics(val leftMargin: Float, val rightEdge: Float, val lineGap: Float)

    private val metrics = pages.map(::metricsOf)
    private val bodyMostlyBold: Boolean = run {
        var bold = 0
        var total = 0
        pages.forEach { p ->
            p.lines.filter { isBodySize(it) }.forEach {
                total += it.text.length
                if (it.bold) bold += it.text.length
            }
        }
        total > 0 && bold > total / 2
    }

    private val result = mutableListOf<Paragraph>()
    private var text = StringBuilder()
    private var paragraphPage = 0
    private var lastPage = 0
    private var marks = mutableListOf<PageMark>()
    private var allHeadingLines = true

    fun build(): List<Paragraph> {
        var prev: TextLine? = null
        var prevPage = -1
        pages.forEachIndexed { pageIdx, page ->
            page.lines.forEach { line ->
                val newParagraph = prev == null || startsNewParagraph(prev!!, metrics[prevPage], line, metrics[pageIdx], prevPage == pageIdx)
                if (newParagraph) {
                    if (prev != null && isDropCap(prev!!, line)) {
                        // Letra capitular: "E" + "ra uma vez" -> "Era uma vez".
                        text.append(line.text)
                        allHeadingLines = false
                    } else {
                        flush()
                        paragraphPage = page.index
                        lastPage = page.index
                        text.append(line.text)
                        allHeadingLines = isHeadingLine(line, metrics[pageIdx])
                    }
                } else {
                    if (page.index != lastPage) {
                        dehyphenator.append(text, line.text)
                        // Início (aproximado) do texto da nova página dentro do parágrafo.
                        marks += PageMark((text.length - line.text.length).coerceIn(0, text.length), page.index)
                        lastPage = page.index
                    } else {
                        dehyphenator.append(text, line.text)
                    }
                    allHeadingLines = allHeadingLines && isHeadingLine(line, metrics[pageIdx])
                }
                prev = line
                prevPage = pageIdx
            }
        }
        flush()
        return result
    }

    private fun flush() {
        val content = text.toString().trim()
        if (content.isNotEmpty()) {
            result += Paragraph(
                text = content,
                page = paragraphPage,
                heading = allHeadingLines || looksLikeHeadingText(content),
                pageMarks = marks.toList(),
            )
        }
        text = StringBuilder()
        marks = mutableListOf()
        allHeadingLines = true
    }

    private fun startsNewParagraph(prev: TextLine, pm: Metrics, cur: TextLine, cm: Metrics, samePage: Boolean): Boolean {
        val prevHeading = isHeadingLine(prev, pm)
        val curHeading = isHeadingLine(cur, cm)
        val gap = cur.baseline - prev.baseline
        val em = cur.fontSize.coerceAtLeast(1f)

        if (prevHeading != curHeading) return true
        if (prevHeading) return !samePage || gap > pm.lineGap * 1.8f || gap < 0

        if (startsWithDialogueDash(cur.text)) return true

        val continuesInPlace = samePage && gap > 0
        if (continuesInPlace && gap > pm.lineGap * 1.3f) return true

        val indented = cur.x0 - cm.leftMargin > em * 0.8f
        val prevSameIndent = continuesInPlace && abs(prev.x0 - cur.x0) < em * 0.5f
        if (indented && !prevSameIndent) return true

        val prevShort = prev.x1 < pm.rightEdge - prev.fontSize * 3f
        // Linha curta e recuada (título centralizado, epígrafe...) seguida de texto na margem.
        val prevIndented = prev.x0 - pm.leftMargin > prev.fontSize * 0.8f
        if (samePage && prevShort && prevIndented && !indented) return true
        if (prevShort && endsSentence(prev.text)) return true
        return false
    }

    private fun isDropCap(prev: TextLine, cur: TextLine): Boolean =
        prev.text.length == 1 && prev.text[0].isUpperCase() &&
            prev.fontSize >= bodySize * 1.8f && cur.text.firstOrNull()?.isLowerCase() == true &&
            text.length == 1 && text[0] == prev.text[0]

    private fun isBodySize(line: TextLine) = abs(line.fontSize - bodySize) <= bodySize * 0.15f

    private fun isHeadingLine(line: TextLine, m: Metrics): Boolean {
        if (line.fontSize >= bodySize * 1.15f) return true
        if (line.bold && !bodyMostlyBold) {
            val words = line.text.split(' ').size
            return words <= 12 && line.x1 < m.rightEdge - line.fontSize * 4f
        }
        return false
    }

    private fun metricsOf(page: PageText): Metrics {
        val body = page.lines.filter(::isBodySize).ifEmpty { page.lines }
        if (body.isEmpty()) return Metrics(0f, Float.MAX_VALUE, bodySize * 1.4f)
        val left = body.groupingBy { Math.round(it.x0) }.eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })!!.key.toFloat()
        val rights = body.map { it.x1 }.sorted()
        val right = rights[((rights.size - 1) * 0.9f).toInt()]
        val gaps = body.zipWithNext { a, b -> b.baseline - a.baseline }
            .filter { it > 0 && it < bodySize * 3f }.sorted()
        val gap = if (gaps.isEmpty()) bodySize * 1.4f else gaps[gaps.size / 2]
        return Metrics(left, right, gap)
    }

    companion object {
        private val HEADING_WORDS = Regex(
            "^(cap[íi]tulo|chapter|parte|part|livro|book|pr[óo]logo|ep[íi]logo|introdu[çc][ãa]o|pref[áa]cio|ap[êe]ndice|posf[áa]cio|sum[áa]rio|[íi]ndice)\\b",
            RegexOption.IGNORE_CASE,
        )

        fun startsWithDialogueDash(s: String): Boolean =
            s.startsWith('—') || s.startsWith('–') || s.startsWith("- ") || s.startsWith("― ")

        fun endsSentence(s: String): Boolean {
            val trimmed = s.trimEnd { it in "\"'”’»)]*" }
            return trimmed.isNotEmpty() && trimmed.last() in ".!?…:;"
        }

        fun looksLikeHeadingText(s: String): Boolean {
            val words = s.split(' ')
            if (words.size > 10 || endsSentence(s) && !s.endsWith(":")) return false
            val letters = s.filter { it.isLetter() }
            if (letters.length >= 3 && letters.all { it.isUpperCase() }) return true
            return words.size <= 8 && HEADING_WORDS.containsMatchIn(s)
        }
    }
}

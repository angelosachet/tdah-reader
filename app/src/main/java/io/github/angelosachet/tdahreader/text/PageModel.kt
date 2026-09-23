package io.github.angelosachet.tdahreader.text

import java.text.Normalizer
import kotlin.math.abs

/**
 * Um caractere posicionado, como informado pelo motor de PDF.
 * Coordenadas em pontos, com o eixo y crescendo para baixo (topo da página = 0).
 */
data class Glyph(
    val text: String,
    val x: Float,
    val baseline: Float,
    val width: Float,
    val fontSize: Float,
    val bold: Boolean,
)

/** Uma linha de texto já montada, com a geometria necessária para a análise de layout. */
data class TextLine(
    val text: String,
    val x0: Float,
    val x1: Float,
    val baseline: Float,
    val fontSize: Float,
    val bold: Boolean,
    /** A linha começava com um marcador sobrescrito (típico de nota de rodapé) que foi removido. */
    val leadingMarker: Boolean = false,
)

data class PageText(
    /** Índice da página no PDF, começando em 0. */
    val index: Int,
    val width: Float,
    val height: Float,
    val lines: List<TextLine>,
)

/**
 * Recebe palavras e quebras de linha do extrator de PDF e monta [TextLine]s.
 * Remove números sobrescritos (chamadas de nota) grudados nas palavras.
 * Não depende de Android nem de uma biblioteca de PDF específica.
 */
class LineAssembler {
    private val words = mutableListOf<Pair<String, List<Glyph>>>()
    private val lines = mutableListOf<TextLine>()

    fun addWord(text: String, glyphs: List<Glyph>) {
        if (glyphs.isEmpty() && text.isBlank()) return
        words += text to glyphs
    }

    fun endLine() {
        buildLine()?.let { lines += it }
        words.clear()
    }

    /** Devolve as linhas acumuladas (a linha pendente é finalizada) e limpa o estado. */
    fun takeLines(): List<TextLine> {
        endLine()
        val result = lines.toList()
        lines.clear()
        return result
    }

    private fun buildLine(): TextLine? {
        val all = words.flatMap { it.second }
        if (all.isEmpty()) return null

        // Tamanho de fonte dominante (ponderado pela quantidade de caracteres).
        val dominant = all.groupingBy { roundHalf(it.fontSize) }
            .fold(0) { acc, g -> acc + g.text.length.coerceAtLeast(1) }
            .maxByOrNull { it.value }!!.key
        val mainGlyphs = all.filter { abs(roundHalf(it.fontSize) - dominant) < 0.01f }
        val baseline = mainGlyphs.map { it.baseline }.sorted()[mainGlyphs.size / 2]

        fun isSuperscriptMarker(g: Glyph): Boolean =
            g.fontSize < dominant * 0.8f &&
                g.baseline < baseline - dominant * 0.2f &&
                g.text.isNotEmpty() && g.text.all { it.isDigit() || it in "*∗†‡" }

        val parts = mutableListOf<String>()
        var leadingMarker = false
        words.forEachIndexed { index, (text, glyphs) ->
            val kept = glyphs.filterNot(::isSuperscriptMarker)
            when {
                kept.size == glyphs.size -> parts += text
                kept.isEmpty() -> if (index == 0) leadingMarker = true
                else -> {
                    if (index == 0 && isSuperscriptMarker(glyphs.first())) leadingMarker = true
                    parts += normalizeLigatures(kept.joinToString("") { it.text })
                }
            }
        }
        val text = parts.joinToString(" ").trim()
        if (text.isEmpty()) return null

        val kept = all.filterNot(::isSuperscriptMarker).ifEmpty { all }
        val boldChars = kept.filter { it.bold }.sumOf { it.text.length }
        val totalChars = kept.sumOf { it.text.length }.coerceAtLeast(1)
        return TextLine(
            text = text,
            x0 = kept.minOf { it.x },
            x1 = kept.maxOf { it.x + it.width },
            baseline = baseline,
            fontSize = dominant,
            bold = boldChars.toFloat() / totalChars >= 0.6f,
            leadingMarker = leadingMarker,
        )
    }

    companion object {
        fun roundHalf(v: Float): Float = Math.round(v * 2f) / 2f

        /** Converte ligaduras tipográficas (ﬁ, ﬂ...) em letras comuns. */
        fun normalizeLigatures(s: String): String {
            if (s.none { it in 'ﬀ'..'﷿' || it in 'ﹰ'..'﻿' }) return s
            val sb = StringBuilder(s.length + 4)
            for (c in s) {
                if (c in 'ﬀ'..'﷿' || c in 'ﹰ'..'﻿') {
                    sb.append(Normalizer.normalize(c.toString(), Normalizer.Form.NFKC).trim())
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}

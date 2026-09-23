package io.github.angelosachet.tdahreader.text

import kotlinx.serialization.Serializable

/** Marca o ponto (dentro do parágrafo) em que começa uma nova página do PDF. */
@Serializable
data class PageMark(val offset: Int, val page: Int)

/** Um parágrafo do livro, já limpo e com as linhas unidas. */
@Serializable
data class Paragraph(
    val text: String,
    /** Página (0-based) em que o parágrafo começa. */
    val page: Int,
    val heading: Boolean = false,
    val pageMarks: List<PageMark> = emptyList(),
) {
    fun pageAt(offset: Int): Int {
        var result = page
        for (mark in pageMarks) {
            if (mark.offset <= offset) result = mark.page else break
        }
        return result
    }
}

/** Item do sumário (marcadores) do PDF. */
@Serializable
data class OutlineEntry(val title: String, val page: Int, val level: Int)

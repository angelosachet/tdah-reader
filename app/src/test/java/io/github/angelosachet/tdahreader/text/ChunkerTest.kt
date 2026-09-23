package io.github.angelosachet.tdahreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    @Test
    fun `uma frase por trecho`() {
        val chunks = Chunker(20).chunk(listOf(Paragraph("Primeira frase aqui. Segunda frase ali.", page = 3)))
        assertEquals(listOf("Primeira frase aqui.", "Segunda frase ali."), chunks.map { it.text })
        assertEquals(listOf(0, 21), chunks.map { it.start })
        assertTrue(chunks.all { it.page == 3 })
        assertEquals(listOf(true, false), chunks.map { it.paragraphStart })
    }

    @Test
    fun `frase longa e quebrada na pontuacao`() {
        val text = "Quando cheguei à casa de meus pais naquela tarde fria de julho, " +
            "encontrei todos reunidos na sala de jantar esperando por notícias da viagem."
        val chunks = Chunker(12).chunk(listOf(Paragraph(text, 0)))
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].text.endsWith("julho,"))
        assertTrue(chunks.all { it.wordCount <= 12 })
    }

    @Test
    fun `respeita o limite de palavras mesmo sem pontuacao`() {
        val text = (1..50).joinToString(" ") { "palavra$it" } + "."
        val chunks = Chunker(10).chunk(listOf(Paragraph(text, 0)))
        assertTrue(chunks.all { it.wordCount <= 10 })
        assertEquals(50, chunks.sumOf { it.wordCount })
    }

    @Test
    fun `frases minusculas se juntam com a seguinte`() {
        val chunks = Chunker(20).chunk(listOf(Paragraph("Ah! Eu sabia que isso ia acontecer.", 0)))
        assertEquals(listOf("Ah! Eu sabia que isso ia acontecer."), chunks.map { it.text })
    }

    @Test
    fun `titulos nao sao divididos e posicoes sao globais`() {
        val chunks = Chunker(20).chunk(
            listOf(Paragraph("CAPÍTULO I", 0, heading = true), Paragraph("Era uma vez. Fim.", 0)),
        )
        assertEquals(listOf("CAPÍTULO I", "Era uma vez.", "Fim."), chunks.map { it.text })
        assertEquals(11, chunks[1].start)
    }

    @Test
    fun `pagina do trecho segue as marcas de pagina`() {
        val p = Paragraph("Começa aqui na página. Termina na outra.", 4, pageMarks = listOf(PageMark(23, 5)))
        val chunks = Chunker(20).chunk(listOf(p))
        assertEquals(listOf(4, 5), chunks.map { it.page })
    }
}

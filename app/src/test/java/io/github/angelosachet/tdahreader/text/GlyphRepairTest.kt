package io.github.angelosachet.tdahreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlyphRepairTest {

    private val adobe = mapOf("T" to "T", "h" to "h", "f" to "f", "i" to "i", "l" to "l", "s" to "s", "t" to "t", "ff" to "ﬀ")

    @Test
    fun `decodifica nomes de ligadura`() {
        assertEquals("Th", GlyphRepair.decodeGlyphName("T_h", adobe::get))
        assertEquals("ffi", GlyphRepair.decodeGlyphName("f_f_i.liga", adobe::get))
        assertEquals("Th", GlyphRepair.decodeGlyphName("Th", adobe::get))
        assertEquals("ﬀ", GlyphRepair.decodeGlyphName("ff", adobe::get))
        assertNull(GlyphRepair.decodeGlyphName(".notdef", adobe::get))
        assertNull(GlyphRepair.decodeGlyphName("glyph123", adobe::get))
    }

    private fun page(vararg lines: String) =
        PageText(0, 600f, 800f, lines.mapIndexed { i, t -> TextLine(t, 72f, 500f, 100f + i * 14f, 11f, false) })

    @Test
    fun `deduz ligadura Th pelo vocabulario do livro`() {
        val th = ''
        val fi = ''
        val result = GlyphRepair.repair(listOf(page(
            "${th}e cat sat on the mat. ${th}at was the end.",
            "${th}ere was a first day and the last day.",
            "It was the ${fi}rst time; she was ${fi}ne and the sun was fine.",
            "The end came first. That day there was a fire.",
        )))
        val text = result[0].lines.joinToString("\n") { it.text }
        assertEquals(
            "The cat sat on the mat. That was the end.\n" +
                "There was a first day and the last day.\n" +
                "It was the first time; she was fine and the sun was fine.\n" +
                "The end came first. That day there was a fire.",
            text,
        )
    }

    @Test
    fun `prefere ligadura quando uma letra tambem formaria palavras`() {
        val th = '\uE062'
        val ft = '\uE117'
        val result = GlyphRepair.repair(listOf(page(
            "${th}e scythe left. ${th}ere he was. ${th}ey knew. ${th}is is it. ${th}en he ran.",
            "She gave a gi$ft and o${ft}en le$ft early. Here he was, then they left, this gift is often there.",
            "The heir and the hose were there; the hen, the hat and the hey.",
        )))
        assertEquals(
            "The scythe left. There he was. They knew. This is it. Then he ran.\n" +
                "She gave a gift and often left early. Here he was, then they left, this gift is often there.",
            result[0].lines.take(2).joinToString("\n") { it.text },
        )
    }

    @Test
    fun `usa o dicionario quando a palavra so aparece com ligadura`() {
        val ft = '\uE09D'
        val result = GlyphRepair.repair(
            listOf(page("She le$ft a${ft}er lunch and o${ft}en came back in the a${ft}ernoon with a gi$ft.")),
        ) { setOf("left", "after", "often", "afternoon", "gift") }
        assertEquals("She left after lunch and often came back in the afternoon with a gift.", result[0].lines[0].text)
    }

    @Test
    fun `nao mexe quando nao ha evidencia`() {
        val odd = ''
        val pages = listOf(page("Um símbolo $odd isolado aqui."))
        assertEquals(pages, GlyphRepair.repair(pages))
    }
}

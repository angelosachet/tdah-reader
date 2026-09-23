package io.github.angelosachet.tdahreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutAnalyzerTest {

    private fun line(text: String, y: Float, x0: Float = 72f, x1: Float = 520f, size: Float = 11f, bold: Boolean = false) =
        TextLine(text, x0, x1, y, size, bold)

    private fun page(index: Int, vararg body: TextLine) = PageText(
        index, 595f, 842f,
        listOf(line("DOM CASMURRO", 40f, x0 = 250f, x1 = 350f, size = 9f)) +
            body.toList() +
            listOf(line("${index + 1}", 800f, x0 = 290f, x1 = 300f, size = 9f)),
    )

    @Test
    fun `remove cabecalho e numero de pagina e une linhas`() {
        val pages = listOf(
            page(0,
                line("CA PÍT ULO I", 120f, x0 = 240f, x1 = 360f, size = 16f),
                line("Uma noite destas, vindo da cidade para o Engenho Novo, encon-", 160f, x0 = 90f),
                line("trei no trem da Central um rapaz aqui do bairro, que eu", 174f),
                line("conheço de vista e de chapéu.", 188f, x1 = 250f),
                line("Cumprimentou-me, sentou-se ao pé de mim, falou da lua e", 202f, x0 = 90f),
            ),
            page(1,
                line("dos ministros, e acabou recitando-me versos.", 60f, x1 = 330f),
                line("— Continue, disse eu acordando.", 74f, x0 = 90f, x1 = 300f),
            ),
            page(2,
                line("A viagem era curta, e os versos pode ser que não fossem (capítulo", 60f, x0 = 90f),
                line("inteiramente maus.", 74f, x1 = 200f),
            ),
        )
        val paragraphs = LayoutAnalyzer().analyze(pages)
        assertEquals(
            listOf(
                "CAPÍTULO I",
                "Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem da Central um rapaz aqui do bairro, que eu conheço de vista e de chapéu.",
                "Cumprimentou-me, sentou-se ao pé de mim, falou da lua e dos ministros, e acabou recitando-me versos.",
                "— Continue, disse eu acordando.",
                "A viagem era curta, e os versos pode ser que não fossem (capítulo inteiramente maus.",
            ),
            paragraphs.map { it.text },
        )
        assertTrue(paragraphs[0].heading)
        assertEquals(listOf(0, 0, 0, 1, 2), paragraphs.map { it.page })
        assertEquals(1, paragraphs[2].pageAt(paragraphs[2].text.indexOf("dos ministros")))
    }

    @Test
    fun `remove nota de rodape em fonte menor`() {
        val pages = listOf(
            PageText(0, 595f, 842f, listOf(
                line("Texto do corpo que termina aqui.", 100f, x1 = 300f),
                line("1 Esta é uma nota de rodapé.", 760f, size = 8f),
            )),
        )
        assertEquals(listOf("Texto do corpo que termina aqui."), LayoutAnalyzer().analyze(pages).map { it.text })
    }
}

package io.github.angelosachet.tdahreader.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitterTest {

    private fun sentences(text: String) = SentenceSplitter.split(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `divide frases simples`() {
        assertEquals(
            listOf("Era uma vez um menino.", "Ele morava longe!", "Por quê?"),
            sentences("Era uma vez um menino. Ele morava longe! Por quê?"),
        )
    }

    @Test
    fun `nao divide em abreviacoes e iniciais`() {
        assertEquals(
            listOf("O Sr. Almeida e a Dra. Paula leram J. R. R. Tolkien na pág. 12.", "Depois saíram."),
            sentences("O Sr. Almeida e a Dra. Paula leram J. R. R. Tolkien na pág. 12. Depois saíram."),
        )
    }

    @Test
    fun `nao divide quando a continuacao e minuscula`() {
        assertEquals(
            listOf("Quem é? perguntou ela... e saiu."),
            sentences("Quem é? perguntou ela... e saiu."),
        )
    }

    @Test
    fun `dialogo com travessao`() {
        assertEquals(
            listOf("— Vamos embora! — gritou ele.", "— Está tarde."),
            sentences("— Vamos embora! — gritou ele. — Está tarde."),
        )
    }

    @Test
    fun `aspas e parenteses fecham a frase`() {
        assertEquals(
            listOf("Ele disse: “Nunca mais.”", "(Mentira.)", "Ela riu."),
            sentences("Ele disse: “Nunca mais.” (Mentira.) Ela riu."),
        )
    }

    @Test
    fun `etc no fim da frase divide`() {
        assertEquals(
            listOf("Comprou pão, leite etc.", "Voltou cedo."),
            sentences("Comprou pão, leite etc. Voltou cedo."),
        )
    }

    @Test
    fun `numeros decimais e datas`() {
        assertEquals(
            listOf("Custou 3.50 reais em 1990.", "Hoje custa mais."),
            sentences("Custou 3.50 reais em 1990. Hoje custa mais."),
        )
    }
}

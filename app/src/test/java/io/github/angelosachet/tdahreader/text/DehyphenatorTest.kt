package io.github.angelosachet.tdahreader.text

import org.junit.Assert.assertEquals
import org.junit.Test

class DehyphenatorTest {
    private val d = Dehyphenator()

    @Test fun `remove hifenizacao comum`() = assertEquals("o computador", d.join("o compu-", "tador"))
    @Test fun `silabas com consoante dupla`() = assertEquals("que passe", d.join("que pas-", "se"))
    @Test fun `gente nao vira gen-te`() = assertEquals("a gente", d.join("a gen-", "te"))
    @Test fun `mantem enclise`() = assertEquals("disse-lhe", d.join("disse-", "lhe"))
    @Test fun `hifen repetido na linha seguinte`() = assertEquals("guarda-chuva", d.join("guarda-", "-chuva"))
    @Test fun `hifen suave`() = assertEquals("leitura", d.join("lei­", "tura"))
    @Test fun `linhas normais ganham espaco`() = assertEquals("uma casa", d.join("uma", "casa"))

    @Test
    fun `vocabulario do livro decide compostos`() {
        val withVocab = Dehyphenator(setOf("bem-vindo"))
        assertEquals("bem-vindo", withVocab.join("bem-", "vindo"))
        assertEquals("seja bemvindo", Dehyphenator().join("seja bem-", "vindo"))
    }
}

package io.github.angelosachet.tdahreader.text

/** Um trecho exibido de uma vez na tela (normalmente uma frase). */
data class Chunk(
    val text: String,
    val paragraphIndex: Int,
    /** Posição global (em caracteres) do início do trecho no livro. É estável entre configurações. */
    val start: Int,
    val page: Int,
    val heading: Boolean,
    /** Primeiro trecho do parágrafo. */
    val paragraphStart: Boolean,
    val wordCount: Int,
)

/**
 * Divide o livro em trechos curtos: uma frase por tela e, quando a frase é longa,
 * pedaços de até [maxWords] palavras quebrados preferencialmente na pontuação.
 */
class Chunker(private val maxWords: Int) {

    init {
        require(maxWords >= 3)
    }

    fun chunk(paragraphs: List<Paragraph>): List<Chunk> {
        val chunks = ArrayList<Chunk>(paragraphs.size * 3)
        var offset = 0
        paragraphs.forEachIndexed { pIndex, paragraph ->
            val pieces = if (paragraph.heading) {
                listOf(0 until paragraph.text.length)
            } else {
                mergeTinySentences(paragraph.text, SentenceSplitter.split(paragraph.text))
                    .flatMap { splitLongSentence(paragraph.text, it) }
            }
            pieces.forEachIndexed { i, range ->
                val text = paragraph.text.substring(range.first, range.last + 1).trim()
                if (text.isNotEmpty()) {
                    chunks += Chunk(
                        text = text,
                        paragraphIndex = pIndex,
                        start = offset + range.first,
                        page = paragraph.pageAt(range.first),
                        heading = paragraph.heading,
                        paragraphStart = i == 0,
                        wordCount = countWords(text),
                    )
                }
            }
            offset += paragraph.text.length + 1
        }
        return chunks
    }

    /** Frases de 1–2 palavras ("Ah!", "Sim.") ficam junto com a seguinte, se couber. */
    private fun mergeTinySentences(text: String, sentences: List<IntRange>): List<IntRange> {
        if (sentences.size < 2) return sentences
        val result = mutableListOf<IntRange>()
        var i = 0
        while (i < sentences.size) {
            var current = sentences[i]
            while (i + 1 < sentences.size &&
                countWords(text.substring(current.first, current.last + 1)) <= 2 &&
                countWords(text.substring(current.first, sentences[i + 1].last + 1)) <= maxWords
            ) {
                current = current.first..sentences[i + 1].last
                i++
            }
            result += current
            i++
        }
        return result
    }

    private data class Token(val start: Int, val end: Int, val text: String, val isWord: Boolean)

    private fun splitLongSentence(text: String, range: IntRange): List<IntRange> {
        val tokens = tokenize(text, range)
        val words = tokens.count { it.isWord }
        if (words <= maxWords) return listOf(range)

        // Programação dinâmica: cada pedaço tem até maxWords palavras e preferimos
        // quebrar em ";", ":", travessões e vírgulas, evitando pedaços minúsculos.
        val n = tokens.size
        val wordPrefix = IntArray(n + 1)
        for (i in 0 until n) wordPrefix[i + 1] = wordPrefix[i] + if (tokens[i].isWord) 1 else 0
        val minWords = (maxWords / 4).coerceIn(2, 5)
        val best = DoubleArray(n + 1) { Double.MAX_VALUE }
        val from = IntArray(n + 1) { -1 }
        best[0] = 0.0
        for (j in 1..n) {
            var i = j - 1
            while (i >= 0) {
                val pieceWords = wordPrefix[j] - wordPrefix[i]
                if (pieceWords > maxWords) break
                if (best[i] != Double.MAX_VALUE) {
                    val breakCost = if (j == n) 0.0 else breakCost(tokens[j - 1], tokens[j])
                    val shortPenalty = if (pieceWords < minWords) (minWords - pieceWords) * 4.0 else 0.0
                    val cost = best[i] + breakCost + shortPenalty + 2.0
                    if (cost < best[j]) {
                        best[j] = cost
                        from[j] = i
                    }
                }
                i--
            }
        }
        if (from[n] < 0) return listOf(range)
        val cuts = mutableListOf<Int>()
        var j = n
        while (j > 0) {
            cuts += j
            j = from[j]
        }
        cuts.reverse()
        val result = mutableListOf<IntRange>()
        var startToken = 0
        for (cut in cuts) {
            result += tokens[startToken].start..tokens[cut - 1].end
            startToken = cut
        }
        return result
    }

    private fun breakCost(before: Token, after: Token): Double {
        val last = before.text.last()
        return when {
            last == ';' || last == ':' -> 0.0
            after.text.first() in "—–―(" -> 0.5
            last == ',' || last == ')' -> 1.0
            last in "—–" -> 1.0
            after.text.lowercase().trim { !it.isLetter() } in CONJUNCTIONS -> 3.0
            else -> 8.0
        }
    }

    private fun tokenize(text: String, range: IntRange): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = range.first
        while (i <= range.last) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }
            val s = i
            while (i <= range.last && !text[i].isWhitespace()) i++
            val t = text.substring(s, i)
            tokens += Token(s, i - 1, t, t.any { it.isLetterOrDigit() })
        }
        return tokens
    }

    companion object {
        private val CONJUNCTIONS = setOf(
            "e", "mas", "porém", "contudo", "todavia", "porque", "pois", "quando", "enquanto", "que",
            "onde", "como", "ou", "nem", "embora", "se", "and", "but", "because", "when", "while",
            "which", "that", "or", "although",
        )

        fun countWords(s: String): Int {
            var count = 0
            var inWord = false
            for (c in s) {
                if (c.isWhitespace()) {
                    inWord = false
                } else if (!inWord) {
                    inWord = true
                    count++
                }
            }
            return count
        }
    }
}

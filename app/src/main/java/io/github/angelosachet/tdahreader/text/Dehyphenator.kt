package io.github.angelosachet.tdahreader.text

/**
 * Junta duas linhas consecutivas de um mesmo parágrafo, desfazendo a hifenização
 * de fim de linha sem destruir palavras compostas ("guarda-chuva") nem ênclises ("disse-lhe").
 *
 * @param vocabulary palavras (minúsculas) vistas no próprio livro fora de fins de linha;
 * é a melhor pista para decidir entre "gente" e "chama-te".
 */
class Dehyphenator(private val vocabulary: Set<String> = emptySet()) {

    fun join(prev: String, next: String): String = StringBuilder(prev).also { append(it, next) }.toString()

    /** Acrescenta [next] ao fim de [sb], unindo as linhas corretamente. */
    fun append(sb: StringBuilder, next: String) {
        if (next.isEmpty()) return
        if (sb.isEmpty()) {
            sb.append(next)
            return
        }
        val last = sb.last()

        if (last == SOFT_HYPHEN) {
            sb.setLength(sb.length - 1)
            sb.append(next)
            return
        }

        val isHyphen = last == '-' || last == '\u2010' || last == '\u2011'
        if (!isHyphen || sb.length < 2 || !sb[sb.length - 2].isLetter()) {
            sb.append(' ').append(next)
            return
        }

        // Convenção tipográfica do português: o hífen do composto é repetido na linha seguinte.
        if (next.first() == '-' || next.first() == '\u2010') {
            sb.append(next, 1, next.length)
            return
        }
        if (!next.first().isLetter() || next.first().isUpperCase()) {
            sb.append(next)
            return
        }

        var start = sb.length - 1
        while (start > 0 && (sb[start - 1].isLetter() || sb[start - 1] == '-')) start--
        val prefix = sb.substring(start, sb.length - 1)
        val suffix = next.takeWhile { it.isLetter() }
        val joined = (prefix + suffix).lowercase()
        val hyphenated = "$prefix-$suffix".lowercase()

        val keepHyphen = when {
            joined in vocabulary -> false
            hyphenated in vocabulary -> true
            else -> looksLikeClitic(prefix.substringAfterLast('-'), suffix.lowercase())
        }
        if (!keepHyphen) sb.setLength(sb.length - 1)
        sb.append(next)
    }

    private fun looksLikeClitic(prefix: String, suffix: String): Boolean {
        if (suffix !in CLITICS) return false
        if (prefix.length < 3) return false
        val p = prefix.lowercase()
        // "pas-se", "car-ro": divisão silábica de consoante dupla, não ênclise.
        if (p.last() == suffix.first() && p.last() in "rs") return false
        return p.last() in "aeiouáéíóúâêôãõàrszm"
    }

    companion object {
        const val SOFT_HYPHEN = '­'

        private val CLITICS = setOf(
            "me", "te", "se", "nos", "vos", "lhe", "lhes",
            "o", "a", "os", "as", "lo", "la", "los", "las", "no", "na", "nas",
            "mo", "ma", "mos", "mas", "to", "ta", "tos", "tas", "lho", "lha", "lhos", "lhas",
            "ei", "ás", "á", "emos", "eis", "ão", "ia", "ias", "íamos", "íeis", "iam",
        )

        /** Extrai as palavras "confiáveis" de uma linha para o vocabulário do livro. */
        fun vocabularyOf(line: String): List<String> {
            val tokens = line.split(' ').filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return emptyList()
            val usable = if (tokens.last().endsWith('-') || tokens.last().endsWith(SOFT_HYPHEN)) {
                tokens.dropLast(1)
            } else {
                tokens
            }
            return usable.mapNotNull { token ->
                val word = token.trim { !it.isLetter() }.lowercase()
                word.takeIf { it.length >= 2 && it.all { c -> c.isLetter() || c == '-' } }
            }
        }
    }
}

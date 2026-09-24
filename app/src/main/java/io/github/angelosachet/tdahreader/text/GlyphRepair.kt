package io.github.angelosachet.tdahreader.text

/**
 * Conserta caracteres que o PDF não soube traduzir para texto — quase sempre
 * ligaduras tipográficas ("Th", "ff", "fi", "ffi", "st"...) desenhadas como um único glifo
 * e que chegam como caracteres de uso privado (aparecem como símbolos) ou códigos de controle.
 */
object GlyphRepair {

    /** O caractere não representa texto legível. */
    fun isSuspicious(c: Char): Boolean =
        c in ''..'' || c == '�' || (c.isISOControl() && c != '\t' && c != '\n')

    /**
     * Converte um nome de glifo em texto: "T_h" -> "Th", "f_f_i.liga" -> "ffi", "Th" -> "Th".
     * [lookup] traduz nomes padrão da Adobe ("T", "uni00E9"...) para Unicode.
     */
    fun decodeGlyphName(name: String?, lookup: (String) -> String?): String? {
        if (name.isNullOrEmpty() || name == ".notdef") return null
        val base = name.substringBefore('.')
        if (base.isEmpty()) return null
        if ('_' in base) {
            val parts = base.split('_').filter { it.isNotEmpty() }
            val decoded = parts.map { part -> lookup(part)?.takeIf { it.none(::isSuspicious) } ?: return null }
            return decoded.joinToString("").takeIf { it.isNotEmpty() }
        }
        lookup(base)?.takeIf { it.isNotEmpty() && it.none(::isSuspicious) }?.let { return it }
        // Nomes compactos de ligadura usados por algumas fontes: "Th", "ffi", "st", "ct".
        return base.takeIf { it.length in 2..3 && it.all { c -> c in 'a'..'z' || c in 'A'..'Z' } }
    }

    private val LIGATURES = listOf(
        "th", "ff", "fi", "fl", "ffi", "ffl", "ft", "st", "ct", "fj", "fb", "fh", "fk", "tt", "sp", "ch", "ck", "qu",
    )
    private val LETTERS = ("abcdefghijklmnopqrstuvwxyz" + "áàâãéêíóôõúüç").map { it.toString() }
    private val WORD = Regex("[\\p{L}\\uE000-\\uF8FF\\uFFFD\\u0000-\\u001F\\u007F-\\u009F]+")

    /**
     * Para cada caractere suspeito que sobrou, testa ligaduras e letras e escolhe a que
     * transforma mais ocorrências em palavras que aparecem no resto do livro.
     */
    fun repair(pages: List<PageText>): List<PageText> {
        val suspicious = HashMap<Char, MutableList<Pair<String, Boolean>>>() // palavra, está no meio da frase
        val vocabulary = HashMap<String, Int>()
        pages.forEach { page ->
            page.lines.forEach { line ->
                var prevEnd = -1
                WORD.findAll(line.text).forEach { m ->
                    val word = m.value
                    if (word.any(::isSuspicious)) {
                        val midSentence = prevEnd >= 0 && line.text.substring(prevEnd, m.range.first).none { it in ".!?:—–\"“" }
                        word.filter(::isSuspicious).toSet().forEach { c ->
                            val list = suspicious.getOrPut(c) { mutableListOf() }
                            if (list.size < 400) list += word to midSentence
                        }
                    } else {
                        vocabulary.merge(word.lowercase(), 1, Int::plus)
                    }
                    prevEnd = m.range.last + 1
                }
            }
        }
        if (suspicious.isEmpty()) return pages

        val replacements = HashMap<Char, String>()
        suspicious.forEach { (c, samples) ->
            var best: String? = null
            var bestScore = 0
            var second = 0
            for (candidate in LIGATURES + LETTERS) {
                val score = samples.count { (word, _) ->
                    val fixed = word.replace(c.toString(), candidate).lowercase()
                    fixed.none(::isSuspicious) && (vocabulary[fixed] ?: 0) > 0
                }
                if (score > bestScore) {
                    second = bestScore; bestScore = score; best = candidate
                } else if (score > second) {
                    second = score
                }
            }
            val confident = best != null && bestScore >= 2 && bestScore >= samples.size * 0.4 && bestScore > second * 2
            if (confident) {
                var replacement = best!!
                // "Th" (maiúsculo) quase nunca aparece no meio da frase.
                val startsWords = samples.count { (w, _) -> w.startsWith(c) }
                val mid = samples.count { (w, m) -> m && w.startsWith(c) }
                if (startsWords > samples.size / 2 && mid < startsWords * 0.3) {
                    replacement = replacement.replaceFirstChar { it.uppercaseChar() }
                }
                replacements[c] = replacement
            }
        }
        if (replacements.isEmpty()) return pages
        return pages.map { page ->
            page.copy(lines = page.lines.map { line ->
                if (line.text.none { it in replacements }) line else {
                    val sb = StringBuilder(line.text.length + 4)
                    line.text.forEach { ch -> sb.append(replacements[ch] ?: ch) }
                    line.copy(text = sb.toString())
                }
            })
        }
    }
}

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
     * transforma mais palavras (distintas) em palavras que aparecem no resto do livro.
     * Ligaduras têm preferência: "#e", "#ere" viram "The"/"There" e não "He"/"Here".
     *
     * @param dictionary palavras comuns (minúsculas) para quando o livro sempre usa a ligadura
     * e a palavra nunca aparece escrita normalmente ("left", "after"). Só é carregado se preciso.
     */
    fun repair(pages: List<PageText>, dictionary: () -> Set<String> = { emptySet() }): List<PageText> {
        class Usage(var count: Int = 0, var wordStart: Int = 0, var midSentence: Int = 0)

        val suspicious = HashMap<Char, HashMap<String, Usage>>()
        val vocabulary = HashSet<String>()
        pages.forEach { page ->
            page.lines.forEach { line ->
                var prevEnd = -1
                WORD.findAll(line.text).forEach { m ->
                    val word = m.value
                    if (word.any(::isSuspicious)) {
                        val mid = prevEnd >= 0 && line.text.substring(prevEnd, m.range.first).none { it in ".!?:—–\"“" }
                        word.filter(::isSuspicious).toSet().forEach { c ->
                            val usage = suspicious.getOrPut(c) { HashMap() }.getOrPut(word) { Usage() }
                            usage.count++
                            if (word.first() == c) {
                                usage.wordStart++
                                if (mid) usage.midSentence++
                            }
                        }
                    } else {
                        vocabulary += word.lowercase()
                    }
                    prevEnd = m.range.last + 1
                }
            }
        }
        if (suspicious.isEmpty()) return pages
        val words = dictionary()

        val replacements = HashMap<Char, String>()
        suspicious.forEach { (c, samples) ->
            fun score(candidate: String) = samples.keys.count { word ->
                val fixed = word.replace(c.toString(), candidate).lowercase()
                fixed.none(::isSuspicious) && (fixed in vocabulary || fixed in words)
            }
            fun confident(score: Int) = score >= 2 && score >= samples.size * 0.4

            val ligature = LIGATURES.maxBy(::score)
            val ligatureScore = score(ligature)
            val letter = LETTERS.maxBy(::score)
            val letterScore = score(letter)
            val chosen = when {
                confident(ligatureScore) && ligatureScore >= letterScore -> ligature
                confident(letterScore) && letterScore > ligatureScore * 2 -> letter
                else -> null
            } ?: return@forEach

            // "Th" (maiúsculo) quase nunca aparece no meio da frase.
            val starts = samples.values.sumOf { it.wordStart }
            val mid = samples.values.sumOf { it.midSentence }
            val total = samples.values.sumOf { it.count }
            replacements[c] = if (starts > total / 2 && mid < starts * 0.3) {
                chosen.replaceFirstChar { it.uppercaseChar() }
            } else {
                chosen
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

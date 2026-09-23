package io.github.angelosachet.tdahreader.text

/**
 * Divide um parágrafo em frases, tratando abreviações comuns do português
 * (e do inglês), iniciais de nomes, reticências e diálogos com travessão.
 */
object SentenceSplitter {

    /** Devolve os intervalos [start, end) de cada frase dentro de [text]. */
    fun split(text: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var sentenceStart = skipSpaces(text, 0)
        var i = sentenceStart
        while (i < text.length) {
            val c = text[i]
            if (c !in TERMINATORS) {
                i++
                continue
            }
            // Agrupa sequências como "?!", "..." e fechamentos: aspas, parênteses.
            var end = i + 1
            while (end < text.length && text[end] in TERMINATORS) end++
            while (end < text.length && text[end] in CLOSERS) end++
            if (end < text.length && !text[end].isWhitespace()) {
                i = end
                continue
            }
            val next = skipSpaces(text, end)
            if (next >= text.length) break
            if (isBoundary(text, i, end, next)) {
                result += sentenceStart until end
                sentenceStart = next
            }
            i = next
        }
        if (sentenceStart < text.length) {
            val end = text.trimEnd().length
            if (end > sentenceStart) result += sentenceStart until end
        }
        return result
    }

    private fun isBoundary(text: String, terminatorAt: Int, end: Int, next: Int): Boolean {
        if (!startsSentence(text, next)) return false
        val single = text[terminatorAt] == '.' && (end - terminatorAt == 1 || text[terminatorAt + 1] in CLOSERS)
        return !(single && isAbbreviation(tokenBefore(text, terminatorAt)))
    }

    /** O próximo trecho começa como uma frase nova? */
    private fun startsSentence(text: String, at: Int): Boolean {
        var j = at
        while (j < text.length && text[j] in OPENERS) j++
        if (j >= text.length) return false
        val c = text[j]
        if (c in DASHES) {
            // "— Vamos! — gritou ele." : travessão seguido de minúscula continua a frase.
            val k = skipSpaces(text, j + 1)
            return k < text.length && (text[k].isUpperCase() || text[k].isDigit() || text[k] in OPENERS)
        }
        return c.isUpperCase() || c.isDigit() || j > at
    }

    private fun tokenBefore(text: String, dotAt: Int): String {
        var s = dotAt
        while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '.' || text[s - 1] == 'º' || text[s - 1] == 'ª')) s--
        return text.substring(s, dotAt)
    }

    private fun isAbbreviation(token: String): Boolean {
        if (token.isEmpty()) return false
        if (token.length == 1) {
            val c = token[0]
            // Iniciais ("J. R. R. Tolkien") e abreviações de uma consoante ("p. 12", "n. 5").
            return (c in 'A'..'Z') || (c.isLowerCase() && c in "bcdfghjklmnpqrstvwxz")
        }
        val lower = token.lowercase()
        if (lower in ABBREVIATIONS) return true
        // "a.C", "V.Exa", "e.g", "U.S": abreviações com pontos internos.
        return lower.contains('.') && lower.split('.').all { it.length <= 3 }
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var j = from
        while (j < text.length && text[j].isWhitespace()) j++
        return j
    }

    private const val TERMINATORS = ".!?…‽"
    private const val CLOSERS = "\"'”’»)]"
    private const val OPENERS = "\"'“‘«(["
    private const val DASHES = "—–―"

    private val ABBREVIATIONS = setOf(
        // português
        "sr", "sra", "srs", "sras", "srta", "srtas", "dr", "dra", "drs", "dras", "prof", "profa", "profs",
        "exmo", "exma", "ilmo", "ilma", "exa", "sto", "sta", "pe", "fr", "gal", "cel", "ten", "sgt", "maj",
        "cmte", "dep", "pres", "eng", "arq", "adv", "av", "al", "pça", "rod", "apto", "ap", "cx", "depto",
        "pág", "págs", "pag", "pp", "cap", "caps", "vol", "vols", "fig", "figs", "tab", "ed", "eds", "org",
        "orgs", "coord", "trad", "obs", "ex", "exs", "cf", "op", "cit", "ibid", "id", "séc", "núm", "num",
        "nº", "jan", "fev", "abr", "jun", "jul", "ago", "nov", "tel", "ltda", "cia", "jr", "aprox", "máx",
        "mín", "gov", "adm", "dir", "sec", "gen", "cia", "fl", "fls", "art", "arts", "inc",
        // inglês
        "mr", "mrs", "ms", "st", "vs", "ltd", "co", "approx", "dept", "est", "lt", "mt", "rev", "capt",
        "col", "cmdr", "no",
    )
}

package io.github.angelosachet.tdahreader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.ceil

/**
 * "Leitura biônica": deixa em negrito o começo de cada palavra,
 * servindo de âncora para os olhos.
 */
fun bionic(text: String, strong: FontWeight = FontWeight.Bold): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (!text[i].isLetter()) {
            append(text[i])
            i++
            continue
        }
        val start = i
        while (i < text.length && text[i].isLetter()) i++
        val len = i - start
        val bold = when {
            len <= 3 -> 1
            len == 4 -> 2
            else -> ceil(len * 0.45).toInt()
        }
        pushStyle(SpanStyle(fontWeight = strong))
        append(text, start, start + bold)
        pop()
        append(text, start + bold, i)
    }
}

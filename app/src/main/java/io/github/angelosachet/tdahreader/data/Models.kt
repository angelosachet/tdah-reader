package io.github.angelosachet.tdahreader.data

import io.github.angelosachet.tdahreader.text.OutlineEntry
import io.github.angelosachet.tdahreader.text.Paragraph
import kotlinx.serialization.Serializable

/** Um livro da biblioteca, com o ponto de leitura salvo. */
@Serializable
data class BookEntry(
    val id: String,
    val title: String,
    val author: String? = null,
    val pageCount: Int,
    val totalChars: Int,
    /** Posição (em caracteres) do trecho em que o leitor parou. */
    val position: Int = 0,
    val addedAt: Long,
    val lastReadAt: Long,
) {
    val progress: Float get() = if (totalChars > 0) (position.toFloat() / totalChars).coerceIn(0f, 1f) else 0f
}

/** Texto já processado do livro, guardado em disco para abrir instantaneamente. */
@Serializable
data class BookContent(
    /** Versão do processamento que gerou este texto (arquivos antigos não têm o campo = 1). */
    val formatVersion: Int = 1,
    val pageCount: Int,
    val paragraphs: List<Paragraph>,
    val outline: List<OutlineEntry> = emptyList(),
) {
    companion object {
        /** 2: ligaduras como "Th" e "ffi" recuperadas; 3: também com ajuda de um dicionário. */
        const val FORMAT_VERSION = 3
    }
}

package io.github.angelosachet.tdahreader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.pdf.PdfTextExtractor
import io.github.angelosachet.tdahreader.text.LayoutAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val fileName: String?, val page: Int = 0, val totalPages: Int = 0) : ImportState
    data class Done(val bookId: String) : ImportState
    data class Failed(val message: String) : ImportState
}

/**
 * Importa um PDF: copia para um arquivo temporário (calculando um hash que identifica o livro),
 * extrai e limpa o texto, desenha a capa e guarda o resultado na biblioteca.
 */
class BookImporter(
    private val context: Context,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()
    private var job: Job? = null

    fun import(uri: Uri) {
        job?.cancel()
        val name = displayName(uri)
        _state.value = ImportState.Working(name)
        job = scope.launch(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "import-${System.nanoTime()}.pdf")
            try {
                _state.value = ImportState.Done(importInternal(uri, name, tmp))
            } catch (e: CancellationException) {
                _state.value = ImportState.Idle
                throw e
            } catch (e: Throwable) {
                _state.value = ImportState.Failed(messageFor(e))
            } finally {
                tmp.delete()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ImportState.Idle
    }

    /** A tela já tratou o resultado (abriu o livro ou mostrou o erro). */
    fun acknowledge() {
        if (_state.value is ImportState.Done || _state.value is ImportState.Failed) _state.value = ImportState.Idle
    }

    private suspend fun importInternal(uri: Uri, name: String?, tmp: File): String {
        val id = copyAndHash(uri, tmp)
        val now = System.currentTimeMillis()
        val existing = library.book(id)
        if (existing != null && library.loadContent(id)?.formatVersion == BookContent.FORMAT_VERSION) {
            // Livros importados antes das capas ganham a sua ao reabrir o PDF.
            ensureCover(id, tmp)
            library.upsert(existing.copy(lastReadAt = now))
            return id
        }

        val context = coroutineContext
        val extracted = PdfTextExtractor.extract(tmp) { page, total ->
            context.ensureActive()
            _state.value = ImportState.Working(name, page + 1, total)
        }
        val paragraphs = withContext(Dispatchers.Default) { LayoutAnalyzer(::commonWords).analyze(extracted.pages) }
        val totalChars = paragraphs.sumOf { it.text.length + 1 }
        val letters = paragraphs.sumOf { p -> p.text.count { it.isLetter() } }
        if (letters < 200 || (extracted.pageCount > 5 && letters < extracted.pageCount * 30)) {
            throw NoTextException()
        }

        val content = BookContent(
            formatVersion = BookContent.FORMAT_VERSION,
            pageCount = extracted.pageCount,
            paragraphs = paragraphs,
            outline = extracted.outline,
        )
        library.saveContent(id, content)
        ensureCover(id, tmp)
        library.upsert(
            BookEntry(
                id = id,
                title = existing?.title ?: chooseTitle(extracted.title, name),
                author = existing?.author ?: extracted.author?.trim()?.takeIf { it.isNotEmpty() && it.length < 100 },
                pageCount = extracted.pageCount,
                totalChars = totalChars,
                // Reprocessando um livro já lido: mantém o progresso (posição proporcional).
                position = existing?.let { (it.progress * totalChars).toInt() } ?: 0,
                addedAt = existing?.addedAt ?: now,
                lastReadAt = now,
            ),
        )
        return id
    }

    /** Uma capa escolhida pela pessoa nunca é substituída. */
    private suspend fun ensureCover(id: String, pdf: File) {
        if (library.hasCover(id)) return
        CoverRenderer.fromPdf(pdf)?.let { library.saveCover(id, it) }
    }

    /** Palavras comuns em inglês e português (assets/words), para reconhecer ligaduras. */
    private fun commonWords(): Set<String> {
        val words = HashSet<String>(100_000)
        for (file in listOf("words/en.txt", "words/pt.txt")) {
            runCatching {
                context.assets.open(file).bufferedReader().useLines { lines -> lines.forEach { if (it.isNotBlank()) words += it } }
            }
        }
        return words
    }

    private suspend fun copyAndHash(uri: Uri, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        input.use { stream ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = stream.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
        }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun chooseTitle(metadataTitle: String?, fileName: String?): String {
        val meta = metadataTitle?.replace(Regex("\\s+"), " ")?.trim()
        val bad = listOf(".htm", ".doc", ".dvi", ".pdf", ".indd", ".tex", "microsoft", "untitled", "sem título", "file:", "\\", "/")
        if (!meta.isNullOrEmpty() && meta.length in 2..120 && bad.none { meta.lowercase().contains(it) }) return meta
        val fromFile = fileName?.substringBeforeLast('.')?.replace('_', ' ')?.trim()
        return fromFile?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.untitled_book)
    }

    private fun messageFor(e: Throwable): String = context.getString(
        when (e) {
            is InvalidPasswordException -> R.string.error_password
            is NoTextException -> R.string.error_no_text
            is OutOfMemoryError -> R.string.error_too_big
            is java.io.FileNotFoundException, is SecurityException -> R.string.error_cannot_open
            else -> R.string.error_invalid_pdf
        },
    )

    private class NoTextException : Exception()
}

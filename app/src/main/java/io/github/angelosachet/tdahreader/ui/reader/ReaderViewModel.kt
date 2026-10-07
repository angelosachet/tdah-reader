package io.github.angelosachet.tdahreader.ui.reader

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.angelosachet.tdahreader.data.BookContent
import io.github.angelosachet.tdahreader.data.LibraryRepository
import io.github.angelosachet.tdahreader.data.SettingsRepository
import io.github.angelosachet.tdahreader.data.StatsRepository
import io.github.angelosachet.tdahreader.text.Chunk
import io.github.angelosachet.tdahreader.text.Chunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Chapter(val title: String, val page: Int, val level: Int, val chunkIndex: Int)

data class ReaderState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val chunks: List<Chunk> = emptyList(),
    val index: Int = 0,
    val finished: Boolean = false,
    val pageCount: Int = 0,
    val totalChars: Int = 1,
    val chapters: List<Chapter> = emptyList(),
    val autoPlaying: Boolean = false,
) {
    val current: Chunk? get() = chunks.getOrNull(index)
    val previous: Chunk? get() = chunks.getOrNull(index - 1)

    val progress: Float
        get() = when {
            finished -> 1f
            chunks.isEmpty() -> 0f
            else -> (current!!.start.toFloat() / totalChars).coerceIn(0f, 1f)
        }

    val currentChapter: Chapter?
        get() = chapters.lastOrNull { it.chunkIndex <= index }
}

class ReaderViewModel(
    private val bookId: String,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val stats: StatsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var content: BookContent? = null
    private var autoJob: Job? = null
    private var autoWpm = 160
    private var dailyGoal = 30

    // Gamificação: só conta frase lida avançando, sem repetir trechos já contados nesta sessão.
    /** Posição (em caracteres) até onde a leitura já foi contada. */
    private var readFrontier = 0
    /** Quando o trecho atual apareceu na tela. */
    private var shownAt = SystemClock.elapsedRealtime()
    private var sessionMillis = 0L

    init {
        viewModelScope.launch {
            val entry = library.book(bookId)
            val loaded = library.loadContent(bookId)
            if (entry == null || loaded == null) {
                _state.value = ReaderState(loading = false, missing = true)
                return@launch
            }
            content = loaded
            readFrontier = entry.position
            _state.update {
                it.copy(title = entry.title, pageCount = loaded.pageCount, totalChars = entry.totalChars.coerceAtLeast(1))
            }
            launch {
                settingsRepository.settings.map { it.autoWpm }.distinctUntilChanged().collect { autoWpm = it }
            }
            launch {
                settingsRepository.settings.map { it.dailyGoal }.distinctUntilChanged().collect { dailyGoal = it }
            }
            settingsRepository.settings.map { it.maxWords }.distinctUntilChanged().collect { maxWords ->
                val position = _state.value.current?.start ?: entry.position
                rechunk(maxWords, position)
            }
        }
    }

    private suspend fun rechunk(maxWords: Int, position: Int) {
        val book = content ?: return
        val (chunks, chapters) = withContext(Dispatchers.Default) {
            val chunks = Chunker(maxWords).chunk(book.paragraphs)
            chunks to buildChapters(book, chunks)
        }
        _state.update {
            it.copy(loading = false, chunks = chunks, chapters = chapters, index = indexFor(chunks, position))
        }
        shownAt = SystemClock.elapsedRealtime()
        scheduleAuto()
    }

    private fun indexFor(chunks: List<Chunk>, position: Int): Int {
        if (chunks.isEmpty()) return 0
        var lo = 0
        var hi = chunks.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chunks[mid].start <= position) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Usa o sumário do PDF; sem sumário, usa os títulos detectados no texto. */
    private fun buildChapters(book: BookContent, chunks: List<Chunk>): List<Chapter> {
        if (chunks.isEmpty()) return emptyList()
        if (book.outline.isNotEmpty()) {
            return book.outline.mapNotNull { entry ->
                val idx = chunks.indexOfFirst { it.page >= entry.page }
                if (idx < 0) null else Chapter(entry.title, entry.page, entry.level, idx)
            }
        }
        val headings = chunks.withIndex()
            .filter { (_, c) -> c.heading && c.paragraphStart && c.text.length <= 80 }
            .map { (i, c) -> Chapter(c.text, c.page, 0, i) }
        // Muitos "títulos" indicam que a detecção não é confiável para este livro.
        return if (headings.size in 2..400) headings.distinctBy { it.chunkIndex } else emptyList()
    }

    fun next() {
        val s = _state.value
        if (s.chunks.isEmpty() || s.finished) return
        val counted = s.current?.let(::recordRead) ?: false
        if (s.index < s.chunks.lastIndex) {
            if (counted && finishesChapter(s)) stats.chapterFinished()
            goTo(s.index + 1)
        } else {
            _state.update { it.copy(finished = true, autoPlaying = false) }
            autoJob?.cancel()
            stats.bookFinished(bookId)
        }
    }

    /**
     * Conta o trecho que sai da tela como lido. Não conta trechos já contados (voltar e avançar)
     * nem toques rápidos demais para alguém ter lido.
     */
    private fun recordRead(chunk: Chunk): Boolean {
        val elapsed = SystemClock.elapsedRealtime() - shownAt
        if (chunk.start < readFrontier || elapsed < MIN_READ_MILLIS) return false
        readFrontier = chunk.start + chunk.text.length
        // Tempo parado na mesma frase (celular de lado, distração) não conta como leitura.
        val millis = elapsed.coerceAtMost(MAX_READ_MILLIS)
        sessionMillis += millis
        stats.sentenceRead(chunk.wordCount, millis, sessionMillis, dailyGoal)
        return true
    }

    /** O próximo trecho começa outro capítulo, e o atual tinha tamanho de capítulo de verdade. */
    private fun finishesChapter(s: ReaderState): Boolean {
        val current = s.chapters.indexOfLast { it.chunkIndex <= s.index }
        val next = s.chapters.indexOfLast { it.chunkIndex <= s.index + 1 }
        if (current < 0 || next <= current) return false
        return s.chapters[next].chunkIndex - s.chapters[current].chunkIndex >= MIN_CHAPTER_CHUNKS
    }

    fun previous() {
        val s = _state.value
        if (s.finished) {
            _state.update { it.copy(finished = false) }
            shownAt = SystemClock.elapsedRealtime()
            scheduleAuto()
        } else if (s.index > 0) {
            goTo(s.index - 1)
        }
    }

    fun goTo(index: Int) {
        val s = _state.value
        if (s.chunks.isEmpty()) return
        val target = index.coerceIn(0, s.chunks.lastIndex)
        _state.update { it.copy(index = target, finished = false) }
        library.updatePosition(bookId, s.chunks[target].start)
        shownAt = SystemClock.elapsedRealtime()
        scheduleAuto()
    }

    fun goToPage(page: Int) {
        val chunks = _state.value.chunks
        val idx = chunks.indexOfFirst { it.page >= page - 1 }
        goTo(if (idx < 0) chunks.lastIndex else idx)
    }

    fun restart() = goTo(0)

    fun toggleAuto() {
        _state.update { it.copy(autoPlaying = !it.autoPlaying && !it.finished) }
        scheduleAuto()
    }

    fun pauseAuto() {
        if (_state.value.autoPlaying) toggleAuto()
    }

    /** Tempo que um trecho fica na tela durante a leitura automática. */
    fun autoDurationMillis(chunk: Chunk): Long {
        val base = chunk.wordCount * 60_000L / autoWpm.coerceAtLeast(1)
        return base.coerceAtLeast(1_500L) + if (chunk.heading || chunk.paragraphStart) 400L else 0L
    }

    private fun scheduleAuto() {
        autoJob?.cancel()
        val s = _state.value
        val chunk = s.current ?: return
        if (!s.autoPlaying || s.finished) return
        autoJob = viewModelScope.launch {
            delay(autoDurationMillis(chunk))
            next()
        }
    }

    override fun onCleared() {
        library.flush()
        stats.flush()
    }

    private companion object {
        const val MIN_READ_MILLIS = 300L
        const val MAX_READ_MILLIS = 60_000L
        /** Seções menores que isso (dedicatória, epígrafe) não contam como capítulo. */
        const val MIN_CHAPTER_CHUNKS = 20
    }
}

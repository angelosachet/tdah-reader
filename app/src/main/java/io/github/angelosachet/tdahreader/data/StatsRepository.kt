package io.github.angelosachet.tdahreader.data

import android.content.Context
import io.github.angelosachet.tdahreader.game.GameEvent
import io.github.angelosachet.tdahreader.game.Progression
import io.github.angelosachet.tdahreader.game.ReadingStats
import io.github.angelosachet.tdahreader.game.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

/**
 * Guarda o progresso da gamificação (stats.json) e avisa as telas do que merece comemoração.
 * As mudanças são aplicadas em ordem, uma de cada vez, depois que o arquivo foi lido.
 */
class StatsRepository(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "stats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()
    private var pendingSave: Job? = null
    private val updates = Channel<(ReadingStats) -> Update>(Channel.UNLIMITED)

    /** Só grava depois de ler o arquivo, para nunca sobrescrever o progresso com dados vazios. */
    @Volatile
    private var loaded = false

    private val _stats = MutableStateFlow(ReadingStats())
    val stats: StateFlow<ReadingStats> = _stats.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _stats.value = runCatching {
                if (file.exists()) json.decodeFromString<ReadingStats>(file.readText()) else ReadingStats()
            }.getOrDefault(ReadingStats())
            loaded = true
            for (op in updates) {
                val result = op(_stats.value)
                if (result.stats != _stats.value) {
                    _stats.value = result.stats
                    scheduleSave()
                }
                result.events.forEach { _events.tryEmit(it) }
            }
        }
    }

    fun sentenceRead(words: Int, millis: Long, sessionMillis: Long, dailyGoal: Int) = submit {
        Progression.sentenceRead(it, words, millis, sessionMillis, dailyGoal, LocalDateTime.now())
    }

    fun chapterFinished() = submit { Progression.chapterFinished(it, LocalDateTime.now()) }

    fun bookFinished(bookId: String) = submit { Progression.bookFinished(it, bookId, LocalDateTime.now()) }

    fun librarySizeChanged(size: Int) = submit { Progression.librarySize(it, size, LocalDateTime.now()) }

    fun flush() {
        pendingSave?.cancel()
        scope.launch { save() }
    }

    private fun submit(op: (ReadingStats) -> Update) {
        updates.trySend(op)
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(1500)
            save()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        if (!loaded) return@withContext
        writeLock.withLock {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(ReadingStats.serializer(), _stats.value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}

package io.github.angelosachet.tdahreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Guarda a lista de livros (library.json), o texto processado de cada um (books/<id>.json)
 * e as capas (covers/<id>.jpg).
 */
class LibraryRepository(context: Context, private val scope: CoroutineScope) {

    private val dir = context.filesDir
    private val libraryFile = File(dir, "library.json")
    private val booksDir = File(dir, "books").apply { mkdirs() }
    private val coversDir = File(dir, "covers").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()
    private var pendingSave: Job? = null

    private val _books = MutableStateFlow<List<BookEntry>>(emptyList())
    val books: StateFlow<List<BookEntry>> = _books.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Volatile
    private var cachedContent: Pair<String, BookContent>? = null

    private val coverCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Muda sempre que uma capa é salva ou apagada, para as telas recarregarem. */
    private val _coverRevision = MutableStateFlow(0)
    val coverRevision: StateFlow<Int> = _coverRevision.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val entries = runCatching {
                if (libraryFile.exists()) json.decodeFromString<List<BookEntry>>(libraryFile.readText()) else emptyList()
            }.getOrDefault(emptyList())
            _books.value = entries.filter { contentFile(it.id).exists() }
            _loaded.value = true
        }
    }

    fun book(id: String): BookEntry? = _books.value.firstOrNull { it.id == id }

    fun hasContent(id: String): Boolean = contentFile(id).exists()

    suspend fun upsert(entry: BookEntry) {
        _books.update { list -> listOf(entry) + list.filterNot { it.id == entry.id } }
        saveLibrary()
    }

    suspend fun remove(id: String) {
        _books.update { list -> list.filterNot { it.id == id } }
        if (cachedContent?.first == id) cachedContent = null
        withContext(Dispatchers.IO) {
            contentFile(id).delete()
            coverFile(id).delete()
        }
        coverCache.remove(id)
        saveLibrary()
    }

    /** Atualiza o ponto de leitura. A gravação em disco é agrupada para não escrever a cada toque. */
    fun updatePosition(id: String, position: Int) {
        _books.update { list ->
            list.map { if (it.id == id) it.copy(position = position, lastReadAt = System.currentTimeMillis()) else it }
        }
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(800)
            saveLibrary()
        }
    }

    fun flush() {
        pendingSave?.cancel()
        scope.launch { saveLibrary() }
    }

    suspend fun loadContent(id: String): BookContent? {
        cachedContent?.let { (cachedId, content) -> if (cachedId == id) return content }
        return withContext(Dispatchers.IO) {
            val file = contentFile(id)
            if (!file.exists()) return@withContext null
            runCatching { json.decodeFromString<BookContent>(file.readText()) }.getOrNull()
                ?.also { cachedContent = id to it }
        }
    }

    suspend fun saveContent(id: String, content: BookContent) = withContext(Dispatchers.IO) {
        if (cachedContent?.first == id) cachedContent = null
        writeAtomically(contentFile(id), json.encodeToString(BookContent.serializer(), content))
        cachedContent = id to content
    }

    fun hasCover(id: String): Boolean = coverFile(id).exists()

    fun cachedCover(id: String): Bitmap? = coverCache.get(id)

    suspend fun loadCover(id: String): Bitmap? {
        coverCache.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = coverFile(id)
            if (!file.exists()) return@withContext null
            runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()?.also { coverCache.put(id, it) }
        }
    }

    suspend fun saveCover(id: String, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            val target = coverFile(id)
            val tmp = File(coversDir, target.name + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
        coverCache.put(id, bitmap)
        _coverRevision.update { it + 1 }
    }

    /** Volta para a capa ilustrada gerada pelo app. */
    suspend fun removeCover(id: String) {
        withContext(Dispatchers.IO) { coverFile(id).delete() }
        coverCache.remove(id)
        _coverRevision.update { it + 1 }
    }

    private suspend fun saveLibrary() = withContext(Dispatchers.IO) {
        writeLock.withLock {
            writeAtomically(libraryFile, json.encodeToString(_books.value))
        }
    }

    private fun contentFile(id: String) = File(booksDir, "$id.json")

    private fun coverFile(id: String) = File(coversDir, "$id.jpg")

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}

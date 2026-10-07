package io.github.angelosachet.tdahreader

import android.content.Context
import android.view.KeyEvent
import io.github.angelosachet.tdahreader.data.BookImporter
import io.github.angelosachet.tdahreader.data.LibraryRepository
import io.github.angelosachet.tdahreader.data.SettingsRepository
import io.github.angelosachet.tdahreader.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dependências compartilhadas pelo app inteiro (injeção manual, sem framework). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val library = LibraryRepository(context, appScope)
    val settings = SettingsRepository(context, appScope)
    val stats = StatsRepository(context, appScope)
    val importer = BookImporter(context, library, appScope)
    val keys = HardwareKeyRouter()
}

/** Encaminha teclas físicas (volume, controles Bluetooth de virar página) para a tela de leitura. */
class HardwareKeyRouter {
    @Volatile
    var handler: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) ?: false
}

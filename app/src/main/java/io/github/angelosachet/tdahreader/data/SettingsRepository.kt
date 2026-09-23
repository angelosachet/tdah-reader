package io.github.angelosachet.tdahreader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, SEPIA, DARK, BLACK }
enum class ReaderFont { LEXEND, ATKINSON, SANS, SERIF }
enum class ReaderAlign { START, CENTER }

data class ReaderSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val font: ReaderFont = ReaderFont.LEXEND,
    val fontSize: Int = 26,
    val lineSpacing: Float = 1.5f,
    /** Máximo de palavras por tela; frases maiores são divididas na pontuação. */
    val maxWords: Int = 20,
    val align: ReaderAlign = ReaderAlign.START,
    val showPrevious: Boolean = false,
    val bionic: Boolean = false,
    val volumeKeys: Boolean = false,
    /** Velocidade da leitura automática, em palavras por minuto. */
    val autoWpm: Int = 160,
    val hintSeen: Boolean = false,
) {
    companion object {
        const val MIN_FONT = 16
        const val MAX_FONT = 44
        const val MIN_WORDS = 5
        const val MAX_WORDS = 40
        const val MIN_WPM = 60
        const val MAX_WPM = 400
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val font = stringPreferencesKey("font")
        val fontSize = intPreferencesKey("font_size")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val maxWords = intPreferencesKey("max_words")
        val align = stringPreferencesKey("align")
        val showPrevious = booleanPreferencesKey("show_previous")
        val bionic = booleanPreferencesKey("bionic")
        val volumeKeys = booleanPreferencesKey("volume_keys")
        val autoWpm = intPreferencesKey("auto_wpm")
        val hintSeen = booleanPreferencesKey("hint_seen")
    }

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.theme] = s.theme.name
            prefs[Keys.font] = s.font.name
            prefs[Keys.fontSize] = s.fontSize
            prefs[Keys.lineSpacing] = s.lineSpacing
            prefs[Keys.maxWords] = s.maxWords
            prefs[Keys.align] = s.align.name
            prefs[Keys.showPrevious] = s.showPrevious
            prefs[Keys.bionic] = s.bionic
            prefs[Keys.volumeKeys] = s.volumeKeys
            prefs[Keys.autoWpm] = s.autoWpm
            prefs[Keys.hintSeen] = s.hintSeen
        }
    }

    private fun Preferences.toSettings(): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            theme = enumOr(this[Keys.theme], d.theme),
            font = enumOr(this[Keys.font], d.font),
            fontSize = (this[Keys.fontSize] ?: d.fontSize).coerceIn(ReaderSettings.MIN_FONT, ReaderSettings.MAX_FONT),
            lineSpacing = (this[Keys.lineSpacing] ?: d.lineSpacing).coerceIn(1.1f, 2.2f),
            maxWords = (this[Keys.maxWords] ?: d.maxWords).coerceIn(ReaderSettings.MIN_WORDS, ReaderSettings.MAX_WORDS),
            align = enumOr(this[Keys.align], d.align),
            showPrevious = this[Keys.showPrevious] ?: d.showPrevious,
            bionic = this[Keys.bionic] ?: d.bionic,
            volumeKeys = this[Keys.volumeKeys] ?: d.volumeKeys,
            autoWpm = (this[Keys.autoWpm] ?: d.autoWpm).coerceIn(ReaderSettings.MIN_WPM, ReaderSettings.MAX_WPM),
            hintSeen = this[Keys.hintSeen] ?: d.hintSeen,
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}

package io.github.angelosachet.tdahreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.data.ReaderFont
import io.github.angelosachet.tdahreader.data.ThemeMode

/** Cores da tela de leitura: fundos suaves e texto sem contraste agressivo. */
@Immutable
data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val track: Color,
    val isDark: Boolean,
)

val LightPalette = ReaderPalette(
    background = Color(0xFFFBFAF6), surface = Color(0xFFF1EFE8), text = Color(0xFF1F1F1F),
    secondaryText = Color(0xFF6B6A66), accent = Color(0xFF3949AB), onAccent = Color.White,
    track = Color(0xFFE2E0D8), isDark = false,
)
val SepiaPalette = ReaderPalette(
    background = Color(0xFFF4ECD8), surface = Color(0xFFEADFC6), text = Color(0xFF3B2F20),
    secondaryText = Color(0xFF7A6A55), accent = Color(0xFF9A5B24), onAccent = Color.White,
    track = Color(0xFFDDCFB2), isDark = false,
)
val DarkPalette = ReaderPalette(
    background = Color(0xFF16171A), surface = Color(0xFF222328), text = Color(0xFFE6E1D6),
    secondaryText = Color(0xFF9A968E), accent = Color(0xFF9FA8FF), onAccent = Color(0xFF14163A),
    track = Color(0xFF34353B), isDark = true,
)
val BlackPalette = ReaderPalette(
    background = Color(0xFF000000), surface = Color(0xFF141414), text = Color(0xFFD6D2C9),
    secondaryText = Color(0xFF8A867E), accent = Color(0xFFFFB74D), onAccent = Color(0xFF2B1A00),
    track = Color(0xFF262626), isDark = true,
)

val LocalReaderPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun paletteFor(mode: ThemeMode): ReaderPalette = when (mode) {
    ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.SEPIA -> SepiaPalette
    ThemeMode.DARK -> DarkPalette
    ThemeMode.BLACK -> BlackPalette
}

private fun ReaderPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = surface, onPrimaryContainer = text,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = track, onSecondaryContainer = text,
        background = background, onBackground = text,
        surface = background, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = secondaryText,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = track,
        outline = secondaryText, outlineVariant = track,
    )
}

@Composable
fun TdahReaderTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val palette = paletteFor(mode)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.isDark
                isAppearanceLightNavigationBars = !palette.isDark
            }
        }
    }
    CompositionLocalProvider(LocalReaderPalette provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}

@OptIn(ExperimentalTextApi::class)
private val Lexend = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(R.font.lexend, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.LEXEND -> Lexend
    ReaderFont.ATKINSON -> Atkinson
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.SERIF -> FontFamily.Serif
}

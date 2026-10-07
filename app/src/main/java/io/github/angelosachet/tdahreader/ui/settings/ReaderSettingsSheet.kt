package io.github.angelosachet.tdahreader.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.data.ReaderAlign
import io.github.angelosachet.tdahreader.data.ReaderFont
import io.github.angelosachet.tdahreader.data.ReaderSettings
import io.github.angelosachet.tdahreader.data.ThemeMode
import io.github.angelosachet.tdahreader.ui.reader.bionic
import io.github.angelosachet.tdahreader.ui.theme.LocalReaderPalette
import io.github.angelosachet.tdahreader.ui.theme.family
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.background,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = palette.text)
            Spacer(Modifier.height(12.dp))

            // Prévia ao vivo
            val preview = stringResource(R.string.settings_preview)
            Text(
                text = if (settings.bionic) bionic(preview) else androidx.compose.ui.text.AnnotatedString(preview),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .padding(20.dp),
                style = TextStyle(
                    fontFamily = settings.font.family(),
                    fontSize = settings.fontSize.coerceAtMost(30).sp,
                    lineHeight = (settings.fontSize.coerceAtMost(30) * settings.lineSpacing).sp,
                    color = palette.text,
                    textAlign = if (settings.align == ReaderAlign.CENTER) TextAlign.Center else TextAlign.Start,
                ),
            )

            Section(R.string.settings_theme)
            ChipRow(
                options = listOf(
                    ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.SEPIA to R.string.theme_sepia, ThemeMode.DARK to R.string.theme_dark,
                    ThemeMode.BLACK to R.string.theme_black,
                ),
                selected = settings.theme,
                onSelect = { v -> onUpdate { it.copy(theme = v) } },
            )

            Section(R.string.settings_font)
            ChipRow(
                options = listOf(
                    ReaderFont.LEXEND to R.string.font_lexend, ReaderFont.ATKINSON to R.string.font_atkinson,
                    ReaderFont.SANS to R.string.font_sans, ReaderFont.SERIF to R.string.font_serif,
                ),
                selected = settings.font,
                onSelect = { v -> onUpdate { it.copy(font = v) } },
            )

            SliderSetting(
                label = stringResource(R.string.settings_font_size, settings.fontSize),
                value = settings.fontSize.toFloat(),
                range = ReaderSettings.MIN_FONT.toFloat()..ReaderSettings.MAX_FONT.toFloat(),
                steps = 0,
                onChange = { v -> onUpdate { it.copy(fontSize = (v / 2).roundToInt() * 2) } },
            )
            SliderSetting(
                label = stringResource(R.string.settings_line_spacing, String.format(Locale("pt", "BR"), "%.1f", settings.lineSpacing)),
                value = settings.lineSpacing,
                range = 1.2f..2.0f,
                steps = 7,
                onChange = { v -> onUpdate { it.copy(lineSpacing = (v * 10).roundToInt() / 10f) } },
            )
            SliderSetting(
                label = stringResource(R.string.settings_max_words, settings.maxWords),
                hint = stringResource(R.string.settings_max_words_hint),
                value = settings.maxWords.toFloat(),
                range = ReaderSettings.MIN_WORDS.toFloat()..ReaderSettings.MAX_WORDS.toFloat(),
                steps = 0,
                onChange = { v -> onUpdate { it.copy(maxWords = v.roundToInt()) } },
            )

            Section(R.string.settings_align)
            ChipRow(
                options = listOf(ReaderAlign.START to R.string.align_start, ReaderAlign.CENTER to R.string.align_center),
                selected = settings.align,
                onSelect = { v -> onUpdate { it.copy(align = v) } },
            )

            Spacer(Modifier.height(8.dp))
            SwitchSetting(R.string.settings_show_previous, R.string.settings_show_previous_hint, settings.showPrevious) { v ->
                onUpdate { it.copy(showPrevious = v) }
            }
            SwitchSetting(R.string.settings_bionic, R.string.settings_bionic_hint, settings.bionic) { v ->
                onUpdate { it.copy(bionic = v) }
            }
            SwitchSetting(R.string.settings_volume_keys, R.string.settings_volume_keys_hint, settings.volumeKeys) { v ->
                onUpdate { it.copy(volumeKeys = v) }
            }
            SliderSetting(
                label = stringResource(R.string.settings_auto_speed, settings.autoWpm),
                hint = stringResource(R.string.settings_auto_speed_hint),
                value = settings.autoWpm.toFloat(),
                range = ReaderSettings.MIN_WPM.toFloat()..ReaderSettings.MAX_WPM.toFloat(),
                steps = 0,
                onChange = { v -> onUpdate { it.copy(autoWpm = (v / 10).roundToInt() * 10) } },
            )
            SwitchSetting(R.string.settings_celebrations, R.string.settings_celebrations_hint, settings.celebrations) { v ->
                onUpdate { it.copy(celebrations = v) }
            }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int) {
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LocalReaderPalette.current.secondaryText,
    )
    Spacer(Modifier.height(6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    val palette = LocalReaderPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent,
                    selectedLabelColor = palette.onAccent,
                    labelColor = palette.text,
                ),
            )
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    hint: String? = null,
) {
    val palette = LocalReaderPalette.current
    Spacer(Modifier.height(16.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.text)
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = palette.secondaryText)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = palette.accent,
            activeTrackColor = palette.accent,
            inactiveTrackColor = palette.track,
            activeTickColor = palette.accent,
            inactiveTickColor = palette.track,
        ),
    )
}

@Composable
private fun SwitchSetting(@StringRes title: Int, @StringRes hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalReaderPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = palette.secondaryText)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

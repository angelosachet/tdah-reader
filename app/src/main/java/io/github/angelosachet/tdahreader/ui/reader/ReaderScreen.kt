package io.github.angelosachet.tdahreader.ui.reader

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Pages
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelosachet.tdahreader.HardwareKeyRouter
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.data.ReaderAlign
import io.github.angelosachet.tdahreader.data.ReaderSettings
import io.github.angelosachet.tdahreader.text.Chunk
import io.github.angelosachet.tdahreader.ui.settings.ReaderSettingsSheet
import io.github.angelosachet.tdahreader.ui.theme.LocalReaderPalette
import io.github.angelosachet.tdahreader.ui.theme.family
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    state: ReaderState,
    settings: ReaderSettings,
    keys: HardwareKeyRouter,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showGoToPage by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }

    val next = { forward = true; viewModel.next() }
    val previous = { forward = false; viewModel.previous() }

    BackHandler(onBack = onBack)
    KeepScreenOn()

    val currentSettings by rememberUpdatedState(settings)
    val onNext by rememberUpdatedState(next)
    val onPrevious by rememberUpdatedState(previous)
    DisposableEffect(keys) {
        keys.handler = handler@{ event ->
            val action = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> if (currentSettings.volumeKeys) onNext else null
                KeyEvent.KEYCODE_VOLUME_UP -> if (currentSettings.volumeKeys) onPrevious else null
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_DOWN -> onNext
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP -> onPrevious
                else -> null
            } ?: return@handler false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) action()
            true
        }
        onDispose { keys.handler = null }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // ------------------------------------------------ barra superior
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_back), tint = palette.secondaryText)
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.currentChapter?.let {
                    Text(
                        it.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { viewModel.pauseAuto(); showSettings = true }) {
                Icon(Icons.Filled.TextFields, stringResource(R.string.reader_settings), tint = palette.secondaryText)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.reader_more), tint = palette.secondaryText)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (state.chapters.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_chapters)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                            onClick = { menuOpen = false; viewModel.pauseAuto(); showChapters = true },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_go_to_page)) },
                        leadingIcon = { Icon(Icons.Outlined.Pages, null) },
                        onClick = { menuOpen = false; viewModel.pauseAuto(); showGoToPage = true },
                    )
                }
            }
        }

        // ------------------------------------------------ frase atual
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .readerGestures(onPrevious = previous, onNext = next),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
                state.finished -> FinishedView(onRestart = { forward = false; viewModel.restart() })
                else -> {
                    AnimatedContent(
                        targetState = state.index,
                        transitionSpec = {
                            val dir = if (forward) 1 else -1
                            (slideInHorizontally(tween(220)) { dir * it / 8 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(160)) { -dir * it / 8 } + fadeOut(tween(160)))
                        },
                        label = "chunk",
                    ) { index ->
                        ChunkView(
                            current = state.chunks.getOrNull(index),
                            previous = if (settings.showPrevious) state.chunks.getOrNull(index - 1) else null,
                            settings = settings,
                        )
                    }
                    if (!settings.hintSeen && !state.loading) {
                        HintCard(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            onDismiss = { onUpdateSettings { it.copy(hintSeen = true) } },
                        )
                    }
                }
            }
        }

        // ------------------------------------------------ progresso e controles
        if (state.autoPlaying && !state.finished) {
            state.current?.let { AutoTimerBar(key = state.index, durationMillis = viewModel.autoDurationMillis(it)) }
        } else {
            Spacer(Modifier.height(3.dp))
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = palette.accent,
            trackColor = palette.track,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = previous,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = palette.surface, contentColor = palette.text),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.reader_previous))
            }
            val page = (state.current?.page ?: 0) + 1
            Text(
                stringResource(R.string.reader_position, page, state.pageCount.coerceAtLeast(page), (state.progress * 100).roundToInt()),
                modifier = Modifier.weight(1f).clickable { viewModel.pauseAuto(); showGoToPage = true },
                style = MaterialTheme.typography.labelMedium,
                color = palette.secondaryText,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = viewModel::toggleAuto) {
                if (state.autoPlaying) {
                    Icon(Icons.Filled.Pause, stringResource(R.string.reader_pause), tint = palette.accent)
                } else {
                    Icon(Icons.Filled.PlayArrow, stringResource(R.string.reader_play), tint = palette.secondaryText)
                }
            }
            FilledTonalIconButton(
                onClick = next,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = palette.accent, contentColor = palette.onAccent),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.reader_next), Modifier.size(30.dp))
            }
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(settings = settings, onUpdate = onUpdateSettings, onDismiss = { showSettings = false })
    }
    if (showChapters) {
        ChaptersSheet(
            chapters = state.chapters,
            current = state.currentChapter,
            onSelect = { forward = true; viewModel.goTo(it.chunkIndex); showChapters = false },
            onDismiss = { showChapters = false },
        )
    }
    if (showGoToPage) {
        GoToPageDialog(
            pageCount = state.pageCount,
            onGo = { forward = true; viewModel.goToPage(it); showGoToPage = false },
            onDismiss = { showGoToPage = false },
        )
    }
}

@Composable
private fun ChunkView(current: Chunk?, previous: Chunk?, settings: ReaderSettings) {
    val palette = LocalReaderPalette.current
    if (current == null) return
    val family = settings.font.family()
    val align = if (settings.align == ReaderAlign.CENTER) TextAlign.Center else TextAlign.Start
    val baseSize = settings.fontSize
    val style = TextStyle(
        fontFamily = family,
        fontSize = if (current.heading) (baseSize * 1.15f).sp else baseSize.sp,
        lineHeight = (baseSize * settings.lineSpacing * if (current.heading) 1.15f else 1f).sp,
        fontWeight = if (current.heading) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current.heading) palette.accent else palette.text,
        textAlign = if (current.heading) TextAlign.Center else align,
    )
    val text: AnnotatedString = if (settings.bionic && !current.heading) bionic(current.text) else AnnotatedString(current.text)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (previous != null) {
                Text(
                    previous.text,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                    style = style.copy(
                        fontSize = (baseSize * 0.72f).sp,
                        lineHeight = (baseSize * 0.72f * settings.lineSpacing).sp,
                        fontWeight = FontWeight.Normal,
                        color = palette.secondaryText.copy(alpha = 0.7f),
                        textAlign = align,
                    ),
                )
                Spacer(Modifier.height((baseSize * 0.9f).dp))
            }
            Text(
                text,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = style,
            )
        }
    }
}

@Composable
private fun FinishedView(onRestart: () -> Unit) {
    val palette = LocalReaderPalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.reader_finished_title),
            style = MaterialTheme.typography.headlineSmall,
            color = palette.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.reader_finished_body),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart) { Text(stringResource(R.string.reader_restart)) }
    }
}

@Composable
private fun HintCard(modifier: Modifier, onDismiss: () -> Unit) {
    val palette = LocalReaderPalette.current
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = palette.surface, tonalElevation = 2.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reader_hint), style = MaterialTheme.typography.bodyMedium, color = palette.text)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.reader_hint_ok))
            }
        }
    }
}

/** Barrinha que enche enquanto a frase está na tela no modo automático. */
@Composable
private fun AutoTimerBar(key: Int, durationMillis: Long) {
    val palette = LocalReaderPalette.current
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key, durationMillis) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis.toInt(), easing = LinearEasing))
    }
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(3.dp),
        color = palette.accent.copy(alpha = 0.5f),
        trackColor = palette.background,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersSheet(chapters: List<Chapter>, current: Chapter?, onSelect: (Chapter) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalReaderPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentIndex = chapters.indexOf(current).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.background) {
        Text(
            stringResource(R.string.reader_chapters),
            style = MaterialTheme.typography.titleLarge,
            color = palette.text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(chapters) { i, chapter ->
                val selected = chapter == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(chapter) }
                        .background(if (selected) palette.surface else palette.background)
                        .padding(start = (24 + 16 * chapter.level.coerceAtMost(3)).dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) palette.accent else palette.text,
                    )
                    Text(
                        stringResource(R.string.chapter_page, chapter.page + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.secondaryText,
                    )
                }
                if (i < chapters.lastIndex) HorizontalDivider(color = palette.track)
            }
        }
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.reader_go_to_page_label, pageCount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let(onGo) }, enabled = page != null) { Text(stringResource(R.string.reader_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Toque na esquerda (30%) volta; no resto avança. Deslizar para a esquerda avança,
 * para a direita volta. Rolagem vertical (texto muito grande) não vira a frase.
 */
private fun Modifier.readerGestures(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val prev by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    pointerInput(Unit) {
        val swipeThreshold = 56.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var last = down
            var moved = false
            var consumed = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) consumed = true
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                last = change
                if (!change.pressed) break
            }
            if (consumed && moved) return@awaitEachGesture
            val dx = last.position.x - down.position.x
            val dy = last.position.y - down.position.y
            val duration = last.uptimeMillis - down.uptimeMillis
            when {
                moved && abs(dx) > swipeThreshold && abs(dx) > abs(dy) * 1.3f -> if (dx < 0) next() else prev()
                !moved && duration < viewConfiguration.longPressTimeoutMillis ->
                    if (down.position.x < size.width * 0.3f) prev() else next()
            }
        }
    }
}

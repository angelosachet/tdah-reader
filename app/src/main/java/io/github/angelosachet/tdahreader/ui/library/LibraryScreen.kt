package io.github.angelosachet.tdahreader.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import io.github.angelosachet.tdahreader.ui.game.countText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.angelosachet.tdahreader.BuildConfig
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.data.BookEntry
import io.github.angelosachet.tdahreader.data.ImportState
import io.github.angelosachet.tdahreader.game.Levels
import io.github.angelosachet.tdahreader.game.ReadingStats
import io.github.angelosachet.tdahreader.ui.game.LevelBadge
import io.github.angelosachet.tdahreader.ui.game.STREAK_ORANGE
import io.github.angelosachet.tdahreader.ui.game.StreakFlame
import io.github.angelosachet.tdahreader.ui.game.formatNumber
import io.github.angelosachet.tdahreader.ui.game.rankTitle
import io.github.angelosachet.tdahreader.ui.game.today
import io.github.angelosachet.tdahreader.ui.theme.LocalReaderPalette
import kotlin.math.roundToInt

/** O que dá para fazer com um livro da estante. */
class BookActions(
    val open: (BookEntry) -> Unit,
    val remove: (BookEntry) -> Unit,
    val changeCover: (BookEntry) -> Unit,
    val resetCover: (BookEntry) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    books: List<BookEntry>,
    loaded: Boolean,
    importState: ImportState,
    stats: ReadingStats,
    dailyGoal: Int,
    covers: CoverSource,
    actions: BookActions,
    onOpenPdf: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    var showAbout by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<BookEntry?>(null) }
    val bookActions = remember(actions) {
        BookActions(open = actions.open, remove = { toRemove = it }, changeCover = actions.changeCover, resetCover = actions.resetCover)
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.EmojiEvents, stringResource(R.string.library_progress_button))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.TextFields, stringResource(R.string.library_settings))
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.library_about))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        floatingActionButton = {
            if (books.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onOpenPdf,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.library_open_pdf)) },
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                )
            }
        },
    ) { padding ->
        when {
            !loaded -> Unit
            books.isEmpty() -> EmptyState(Modifier.padding(padding), onOpenPdf)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val sorted = books.sortedByDescending { it.lastReadAt }
                item(key = "progress", span = { GridItemSpan(maxLineSpan) }) {
                    ProgressHeader(stats, dailyGoal, onOpenStats)
                }
                item(key = "continue-label", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(stringResource(R.string.library_continue))
                }
                item(key = "continue-" + sorted.first().id, span = { GridItemSpan(maxLineSpan) }) {
                    val book = sorted.first()
                    ContinueCard(book, rememberCover(covers, book.id), book.id in stats.finishedBooks, bookActions)
                }
                if (sorted.size > 1) {
                    item(key = "shelf-label", span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(stringResource(R.string.library_other_books))
                    }
                    items(sorted.drop(1), key = { it.id }) { book ->
                        ShelfTile(book, rememberCover(covers, book.id), book.id in stats.finishedBooks, bookActions)
                    }
                }
            }
        }
    }

    ImportDialogs(importState, onCancelImport, onDismissError)

    toRemove?.let { book ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.library_remove_title)) },
            text = { Text(stringResource(R.string.library_remove_body, book.title)) },
            confirmButton = {
                TextButton(onClick = { actions.remove(book); toRemove = null }) {
                    Text(stringResource(R.string.library_remove_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = { Text(stringResource(R.string.about_body, BuildConfig.VERSION_NAME)) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalReaderPalette.current.secondaryText,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** Nível, XP, sequência de dias e a meta de hoje. */
@Composable
private fun ProgressHeader(stats: ReadingStats, dailyGoal: Int, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    val today = today()
    val done = stats.day(today).sentences
    val met = done >= dailyGoal
    val streak = stats.streak(today)
    val level = stats.level
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface, contentColor = palette.text),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(level)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.level_title, level, rankTitle(level)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    GameBar(Levels.progress(stats.xp), palette.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.level_xp_to_next,
                            formatNumber(stats.xp - Levels.xpFor(level)),
                            formatNumber(Levels.xpFor(level + 1) - Levels.xpFor(level)),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StreakFlame(streak)
                    Text(
                        countText(R.plurals.streak_days_short, streak),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (met) stringResource(R.string.goal_today_done, dailyGoal)
                else stringResource(R.string.goal_today_progress, done, dailyGoal),
                style = MaterialTheme.typography.bodyMedium,
                color = if (met) STREAK_ORANGE else palette.text,
                fontWeight = if (met) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.height(6.dp))
            GameBar(done.toFloat() / dailyGoal.coerceAtLeast(1), if (met) STREAK_ORANGE else palette.accent)
        }
    }
}

@Composable
private fun GameBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = LocalReaderPalette.current.track,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@Composable
private fun progressText(book: BookEntry, short: Boolean = false): String {
    if (book.position == 0) return stringResource(R.string.never_read)
    val percent = (book.progress * 100).roundToInt()
    if (short) return stringResource(R.string.library_progress_short, percent)
    val page = (book.progress * book.pageCount).roundToInt().coerceIn(1, book.pageCount.coerceAtLeast(1))
    return stringResource(R.string.library_progress, percent, page, book.pageCount)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    book: BookEntry,
    cover: ImageBitmap?,
    finished: Boolean,
    actions: BookActions,
) {
    val palette = LocalReaderPalette.current
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = { actions.open(book) }, onLongClick = { menu = true }),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface, contentColor = palette.text),
    ) {
        Row(
            Modifier.height(IntrinsicSize.Min).padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BookCover(book, cover, Modifier.width(96.dp), finished)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                GameBar(book.progress, palette.accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    progressText(book) + if (book.position == 0) "" else " · " + DateUtils.getRelativeTimeSpanString(book.lastReadAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondaryText,
                )
                Spacer(Modifier.weight(1f).height(12.dp))
                FilledTonalButton(
                    onClick = { actions.open(book) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (book.position == 0) R.string.library_start else R.string.library_resume))
                }
            }
            BookMenu(book, cover != null, menu, onOpen = { menu = true }, onDismiss = { menu = false }, actions = actions)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfTile(
    book: BookEntry,
    cover: ImageBitmap?,
    finished: Boolean,
    actions: BookActions,
) {
    val palette = LocalReaderPalette.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = { actions.open(book) }, onLongClick = { menu = true })
            .padding(2.dp),
    ) {
        BookCover(book, cover, Modifier.fillMaxWidth(), finished)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { book.progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (finished) Color(0xFF2E7D32) else palette.accent,
            trackColor = palette.track,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    progressText(book, short = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BookMenu(book, cover != null, menu, onOpen = { menu = true }, onDismiss = { menu = false }, actions = actions, compact = true)
        }
    }
}

@Composable
private fun BookMenu(
    book: BookEntry,
    hasCover: Boolean,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    actions: BookActions,
    compact: Boolean = false,
) {
    val palette = LocalReaderPalette.current
    Box {
        IconButton(onClick = onOpen, modifier = if (compact) Modifier.size(32.dp) else Modifier) {
            Icon(
                Icons.Filled.MoreVert,
                stringResource(R.string.library_book_options),
                tint = palette.secondaryText,
                modifier = if (compact) Modifier.size(18.dp) else Modifier,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_change_cover)) },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { onDismiss(); actions.changeCover(book) },
            )
            if (hasCover) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_reset_cover)) },
                    leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                    onClick = { onDismiss(); actions.resetCover(book) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_remove)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { onDismiss(); actions.remove(book) },
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onOpenPdf: () -> Unit) {
    val palette = LocalReaderPalette.current
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(72.dp), tint = palette.accent)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = palette.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onOpenPdf, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.library_open_pdf), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ImportDialogs(state: ImportState, onCancel: () -> Unit, onDismissError: () -> Unit) {
    val palette = LocalReaderPalette.current
    when (state) {
        is ImportState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column {
                    state.fileName?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (state.totalPages > 0) {
                        LinearProgressIndicator(
                            progress = { state.page.toFloat() / state.totalPages },
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.accent,
                            trackColor = palette.track,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.import_page, state.page, state.totalPages),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondaryText,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent, trackColor = palette.track)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.import_reading_file), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.import_hint), style = MaterialTheme.typography.bodySmall, color = palette.secondaryText)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.ok)) } },
        )
        else -> Unit
    }
}

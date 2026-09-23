package io.github.angelosachet.tdahreader.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.angelosachet.tdahreader.BuildConfig
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.data.BookEntry
import io.github.angelosachet.tdahreader.data.ImportState
import io.github.angelosachet.tdahreader.ui.theme.LocalReaderPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    books: List<BookEntry>,
    loaded: Boolean,
    importState: ImportState,
    onOpenPdf: () -> Unit,
    onOpenBook: (BookEntry) -> Unit,
    onRemoveBook: (BookEntry) -> Unit,
    onCancelImport: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    var showAbout by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<BookEntry?>(null) }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
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
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sorted = books.sortedByDescending { it.lastReadAt }
                item { SectionLabel(stringResource(R.string.library_continue)) }
                item { BookCard(sorted.first(), highlight = true, onOpen = onOpenBook, onRemove = { toRemove = it }) }
                if (sorted.size > 1) {
                    item { SectionLabel(stringResource(R.string.library_other_books)) }
                    items(sorted.drop(1), key = { it.id }) { book ->
                        BookCard(book, highlight = false, onOpen = onOpenBook, onRemove = { toRemove = it })
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
                TextButton(onClick = { onRemoveBook(book); toRemove = null }) {
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
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(book: BookEntry, highlight: Boolean, onOpen: (BookEntry) -> Unit, onRemove: (BookEntry) -> Unit) {
    val palette = LocalReaderPalette.current
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = { onOpen(book) }, onLongClick = { menu = true }),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface, contentColor = palette.text),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = palette.accent,
                    trackColor = palette.track,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
                Spacer(Modifier.height(8.dp))
                val percent = (book.progress * 100).roundToInt()
                val page = (book.progress * book.pageCount).roundToInt().coerceIn(1, book.pageCount.coerceAtLeast(1))
                Text(
                    if (book.position == 0) stringResource(R.string.never_read)
                    else stringResource(R.string.library_progress, percent, page, book.pageCount) +
                        " · " + DateUtils.getRelativeTimeSpanString(book.lastReadAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondaryText,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.library_book_options), tint = palette.secondaryText)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_remove)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onRemove(book) },
                    )
                }
            }
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

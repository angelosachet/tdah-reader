package io.github.angelosachet.tdahreader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.angelosachet.tdahreader.AppContainer
import io.github.angelosachet.tdahreader.data.CoverRenderer
import io.github.angelosachet.tdahreader.data.ImportState
import io.github.angelosachet.tdahreader.data.ReaderSettings
import io.github.angelosachet.tdahreader.ui.game.CelebrationHost
import io.github.angelosachet.tdahreader.ui.game.today
import io.github.angelosachet.tdahreader.ui.library.BookActions
import io.github.angelosachet.tdahreader.ui.library.CoverSource
import io.github.angelosachet.tdahreader.ui.library.LibraryScreen
import io.github.angelosachet.tdahreader.ui.reader.ReaderScreen
import io.github.angelosachet.tdahreader.ui.reader.ReaderViewModel
import io.github.angelosachet.tdahreader.ui.settings.ReaderSettingsSheet
import io.github.angelosachet.tdahreader.ui.stats.StatsScreen
import io.github.angelosachet.tdahreader.ui.theme.TdahReaderTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
data class ReaderRoute(val bookId: String)

@Serializable
object StatsRoute

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settings.current.collectAsStateWithLifecycle()
    val current = settings ?: return // espera as preferências para não piscar o tema errado
    val scope = rememberCoroutineScope()
    val update: ((ReaderSettings) -> ReaderSettings) -> Unit = { t -> scope.launch { container.settings.update(t) } }

    TdahReaderTheme(current.theme) {
        val nav = rememberNavController()
        val importState by container.importer.state.collectAsStateWithLifecycle()
        val stats by container.stats.stats.collectAsStateWithLifecycle()
        val coverRevision by container.library.coverRevision.collectAsStateWithLifecycle()
        val covers = remember(coverRevision) {
            CoverSource(coverRevision, container.library::cachedCover, container.library::loadCover)
        }

        // Um PDF chegou por "Abrir com"/"Compartilhar" fora da estante: volta para ela, que abre o livro.
        LaunchedEffect(importState) {
            if (importState is ImportState.Working && nav.currentBackStackEntry?.destination?.hasRoute<LibraryRoute>() == false) {
                nav.popBackStack(LibraryRoute, inclusive = false)
            }
        }

        Box(Modifier.fillMaxSize()) {
            NavHost(navController = nav, startDestination = LibraryRoute) {
                composable<LibraryRoute> {
                    val books by container.library.books.collectAsStateWithLifecycle()
                    val loaded by container.library.loaded.collectAsStateWithLifecycle()
                    var showSettings by remember { mutableStateOf(false) }
                    var coverTarget by remember { mutableStateOf<String?>(null) }
                    val context = LocalContext.current
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            container.importer.import(uri)
                        }
                    }
                    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        val id = coverTarget
                        coverTarget = null
                        if (uri != null && id != null) {
                            scope.launch { CoverRenderer.fromImage(context, uri)?.let { container.library.saveCover(id, it) } }
                        }
                    }
                    LaunchedEffect(importState) {
                        val done = importState as? ImportState.Done ?: return@LaunchedEffect
                        container.importer.acknowledge()
                        nav.navigate(ReaderRoute(done.bookId)) { launchSingleTop = true }
                    }
                    LaunchedEffect(loaded, books.size) {
                        if (loaded) container.stats.librarySizeChanged(books.size)
                    }
                    val actions = remember {
                        BookActions(
                            open = { nav.navigate(ReaderRoute(it.id)) { launchSingleTop = true } },
                            remove = { scope.launch { container.library.remove(it.id) } },
                            changeCover = {
                                coverTarget = it.id
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            resetCover = { scope.launch { container.library.removeCover(it.id) } },
                        )
                    }
                    LibraryScreen(
                        books = books,
                        loaded = loaded,
                        importState = importState,
                        stats = stats,
                        dailyGoal = current.dailyGoal,
                        covers = covers,
                        actions = actions,
                        onOpenPdf = { picker.launch(arrayOf("application/pdf")) },
                        onCancelImport = container.importer::cancel,
                        onDismissError = container.importer::acknowledge,
                        onOpenSettings = { showSettings = true },
                        onOpenStats = { nav.navigate(StatsRoute) { launchSingleTop = true } },
                    )
                    if (showSettings) ReaderSettingsSheet(current, update) { showSettings = false }
                }
                composable<ReaderRoute> { entry ->
                    val bookId = entry.toRoute<ReaderRoute>().bookId
                    val vm = viewModel { ReaderViewModel(bookId, container.library, container.settings, container.stats) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    LaunchedEffect(state.missing) { if (state.missing) nav.popBackStack() }
                    ReaderScreen(
                        viewModel = vm,
                        state = state,
                        settings = current,
                        keys = container.keys,
                        todaySentences = stats.day(today()).sentences,
                        onUpdateSettings = update,
                        onOpenStats = { nav.navigate(StatsRoute) { launchSingleTop = true } },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable<StatsRoute> {
                    StatsScreen(
                        stats = stats,
                        settings = current,
                        onUpdateSettings = update,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            CelebrationHost(events = container.stats.events, enabled = current.celebrations)
        }
    }
}

package io.github.angelosachet.tdahreader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.angelosachet.tdahreader.AppContainer
import io.github.angelosachet.tdahreader.data.ImportState
import io.github.angelosachet.tdahreader.data.ReaderSettings
import io.github.angelosachet.tdahreader.ui.library.LibraryScreen
import io.github.angelosachet.tdahreader.ui.reader.ReaderScreen
import io.github.angelosachet.tdahreader.ui.reader.ReaderViewModel
import io.github.angelosachet.tdahreader.ui.settings.ReaderSettingsSheet
import io.github.angelosachet.tdahreader.ui.theme.TdahReaderTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
data class ReaderRoute(val bookId: String)

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settings.current.collectAsStateWithLifecycle()
    val current = settings ?: return // espera as preferências para não piscar o tema errado
    val scope = rememberCoroutineScope()
    val update: ((ReaderSettings) -> ReaderSettings) -> Unit = { t -> scope.launch { container.settings.update(t) } }

    TdahReaderTheme(current.theme) {
        val nav = rememberNavController()
        val importState by container.importer.state.collectAsStateWithLifecycle()

        // Um PDF chegou por "Abrir com"/"Compartilhar" enquanto um livro estava aberto: volta à estante.
        LaunchedEffect(importState) {
            if (importState is ImportState.Working && nav.currentBackStackEntry?.destination?.route?.contains("ReaderRoute") == true) {
                nav.popBackStack(LibraryRoute, inclusive = false)
            }
        }

        NavHost(navController = nav, startDestination = LibraryRoute) {
            composable<LibraryRoute> {
                val books by container.library.books.collectAsStateWithLifecycle()
                val loaded by container.library.loaded.collectAsStateWithLifecycle()
                var showSettings by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        container.importer.import(uri)
                    }
                }
                LaunchedEffect(importState) {
                    val done = importState as? ImportState.Done ?: return@LaunchedEffect
                    container.importer.acknowledge()
                    nav.navigate(ReaderRoute(done.bookId)) { launchSingleTop = true }
                }
                LibraryScreen(
                    books = books,
                    loaded = loaded,
                    importState = importState,
                    onOpenPdf = { picker.launch(arrayOf("application/pdf")) },
                    onOpenBook = { nav.navigate(ReaderRoute(it.id)) { launchSingleTop = true } },
                    onRemoveBook = { scope.launch { container.library.remove(it.id) } },
                    onCancelImport = container.importer::cancel,
                    onDismissError = container.importer::acknowledge,
                    onOpenSettings = { showSettings = true },
                )
                if (showSettings) ReaderSettingsSheet(current, update) { showSettings = false }
            }
            composable<ReaderRoute> { entry ->
                val bookId = entry.toRoute<ReaderRoute>().bookId
                val vm = viewModel { ReaderViewModel(bookId, container.library, container.settings) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.missing) { if (state.missing) nav.popBackStack() }
                ReaderScreen(
                    viewModel = vm,
                    state = state,
                    settings = current,
                    keys = container.keys,
                    onUpdateSettings = update,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

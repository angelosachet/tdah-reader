package io.github.angelosachet.tdahreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import io.github.angelosachet.tdahreader.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val container get() = (application as TdahReaderApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        container.library.flush()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        container.keys.dispatch(event) || super.dispatchKeyEvent(event)

    /** PDFs recebidos por "Abrir com" ou "Compartilhar". */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) container.importer.import(uri)
    }
}

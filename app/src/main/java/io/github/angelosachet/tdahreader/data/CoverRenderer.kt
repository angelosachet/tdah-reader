package io.github.angelosachet.tdahreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Gera as miniaturas de capa: da primeira página do PDF ou de uma imagem escolhida. */
object CoverRenderer {
    private const val WIDTH = 300
    private const val MAX_HEIGHT = 600

    /** Desenha a primeira página que não esteja em branco (olhando até a terceira). */
    fun fromPdf(file: File): Bitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val renderer = PdfRenderer(fd)
            try {
                var cover: Bitmap? = null
                for (i in 0 until minOf(3, renderer.pageCount)) {
                    val bitmap = renderPage(renderer, i)
                    if (!isBlank(bitmap)) {
                        cover = bitmap
                        break
                    }
                    bitmap.recycle()
                }
                cover
            } finally {
                renderer.close()
            }
        }
    }.getOrNull()

    /** Lê uma imagem da galeria, já reduzida para o tamanho de capa. */
    suspend fun fromImage(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // O ImageDecoder já aplica a rotação EXIF das fotos.
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = WIDTH * 2f / info.size.width
                    if (scale < 1f) {
                        decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= WIDTH * 2) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
            decoded?.let(::fit)
        }.getOrNull()
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val height = (WIDTH * page.height.toFloat() / page.width.coerceAtLeast(1)).roundToInt().coerceIn(1, MAX_HEIGHT)
            val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    /** Página praticamente toda branca (guarda, folha de rosto vazia). */
    private fun isBlank(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var inked = 0
        var samples = 0
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val p = pixels[y * w + x]
                samples++
                if (Color.red(p) < 225 || Color.green(p) < 225 || Color.blue(p) < 225) inked++
            }
        }
        return inked < samples * 0.004
    }

    /** Recorta imagens compridas demais e reduz para a largura de capa. */
    private fun fit(source: Bitmap): Bitmap {
        val cropped = if (source.height > source.width * 2) {
            val h = source.width * 2
            Bitmap.createBitmap(source, 0, (source.height - h) / 2, source.width, h)
        } else {
            source
        }
        if (cropped.width <= WIDTH) return cropped
        val height = (WIDTH * cropped.height.toFloat() / cropped.width).roundToInt().coerceIn(1, MAX_HEIGHT)
        return Bitmap.createScaledBitmap(cropped, WIDTH, height, true)
    }
}

package io.github.angelosachet.tdahreader.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.data.BookEntry
import kotlin.math.abs

/** Acesso às capas para as telas; [revision] muda sempre que alguma capa é salva ou apagada. */
class CoverSource(
    val revision: Int,
    val cached: (String) -> Bitmap?,
    val load: suspend (String) -> Bitmap?,
)

@Composable
fun rememberCover(covers: CoverSource, bookId: String): ImageBitmap? {
    val initial = remember(bookId, covers.revision) { covers.cached(bookId)?.asImageBitmap() }
    val cover by produceState(initial, bookId, covers.revision) {
        value = covers.load(bookId)?.asImageBitmap()
    }
    return cover
}

/**
 * Capa do livro em proporção 2:3, com uma lombada sombreada à esquerda.
 * Sem imagem, desenha uma capa ilustrada com o título e as cores escolhidas pelo id do livro.
 */
@Composable
fun BookCover(book: BookEntry, cover: ImageBitmap?, modifier: Modifier = Modifier, finished: Boolean = false) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(6.dp, shape)
            .clip(shape),
    ) {
        if (cover != null) {
            Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            GeneratedCover(book)
        }
        // Lombada: sombra e brilho que dão volume de livro.
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.09f)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.32f), Color.White.copy(alpha = 0.12f), Color.Transparent),
                    ),
                ),
        )
        if (finished) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.library_finished),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private val COVER_COLORS = listOf(
    Color(0xFF283593) to Color(0xFF5C6BC0),
    Color(0xFF00695C) to Color(0xFF26A69A),
    Color(0xFF6A1B9A) to Color(0xFFAB47BC),
    Color(0xFFAD1457) to Color(0xFFEC407A),
    Color(0xFFBF360C) to Color(0xFFFF7043),
    Color(0xFF37474F) to Color(0xFF78909C),
    Color(0xFF4E342E) to Color(0xFF8D6E63),
    Color(0xFF1565C0) to Color(0xFF42A5F5),
)

@Composable
private fun GeneratedCover(book: BookEntry) {
    val (dark, light) = COVER_COLORS[abs(book.id.hashCode()) % COVER_COLORS.size]
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(light, dark))),
    ) {
        val unit = maxWidth.value
        // Tamanhos proporcionais à capa, sem crescer com a escala de fonte do sistema (a capa é um desenho).
        val density = LocalDensity.current
        fun size(fraction: Float) = with(density) { (unit * fraction).dp.toSp() }
        // A palavra mais longa precisa caber inteira numa linha.
        val longest = book.title.split(' ').maxOf { it.length }.coerceAtLeast(1)
        val titleFraction = minOf(0.13f, 0.76f / (longest * 0.6f)).coerceAtLeast(0.05f)
        Column(
            Modifier.fillMaxSize().padding(start = (unit * 0.14f).dp, end = (unit * 0.08f).dp, top = (unit * 0.16f).dp),
        ) {
            Box(Modifier.width((unit * 0.22f).dp).height(2.dp).background(Color.White.copy(alpha = 0.7f)))
            Spacer(Modifier.height((unit * 0.08f).dp))
            Text(
                book.title,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = size(titleFraction),
                lineHeight = size(titleFraction * 1.2f),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let {
                Spacer(Modifier.height((unit * 0.06f).dp))
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = size(0.085f),
                    lineHeight = size(0.1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

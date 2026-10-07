package io.github.angelosachet.tdahreader.ui.game

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.game.GameEvent
import io.github.angelosachet.tdahreader.ui.theme.LocalReaderPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * Mostra, por cima de qualquer tela, um aviso para cada [GameEvent] (um de cada vez)
 * e confete nas comemorações maiores. Com [enabled] desligado, nada aparece.
 */
@Composable
fun CelebrationHost(events: Flow<GameEvent>, enabled: Boolean) {
    val queue = remember { mutableStateListOf<GameEvent>() }
    val isEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(events) {
        events.collect { if (isEnabled) queue += it }
    }
    var shown by remember { mutableStateOf<GameEvent?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = snapshotFlow { queue.firstOrNull() }.filterNotNull().first()
            shown = next
            visible = true
            delay(if (next is GameEvent.ChapterDone) 2_200L else 3_000L)
            visible = false
            delay(350) // dá tempo da saída animar antes do próximo aviso
            queue.remove(next)
        }
    }
    val current = shown?.takeIf { visible }

    Box(Modifier.fillMaxSize()) {
        if (current != null && current !is GameEvent.ChapterDone) Confetti(trigger = current)
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 60.dp, start = 16.dp, end = 16.dp),
        ) {
            shown?.let { CelebrationBanner(it) }
        }
    }
}

@Composable
private fun CelebrationBanner(event: GameEvent) {
    val palette = LocalReaderPalette.current
    val (icon: ImageVector, title: String, subtitle: String) = when (event) {
        is GameEvent.GoalReached -> Triple(
            Icons.Filled.LocalFireDepartment,
            stringResource(R.string.celebrate_goal),
            countText(R.plurals.celebrate_goal_streak, event.streak),
        )
        is GameEvent.LevelUp -> Triple(
            Icons.AutoMirrored.Filled.TrendingUp,
            stringResource(R.string.celebrate_level, event.level),
            rankTitle(event.level),
        )
        is GameEvent.Unlocked -> Triple(
            event.achievement.icon,
            stringResource(R.string.celebrate_achievement),
            stringResource(event.achievement.titleRes),
        )
        is GameEvent.ChapterDone -> Triple(
            Icons.Filled.AutoStories,
            stringResource(R.string.celebrate_chapter),
            stringResource(R.string.celebrate_xp, event.xp),
        )
        is GameEvent.BookDone -> Triple(
            Icons.Filled.EmojiEvents,
            stringResource(R.string.celebrate_book),
            stringResource(R.string.celebrate_xp, event.xp),
        )
    }
    val iconColor = if (event is GameEvent.GoalReached) STREAK_ORANGE else palette.accent
    Row(
        Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.text)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
        }
    }
}

private class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val rotation: Float,
    val width: Float,
    val height: Float,
    val color: Color,
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFFC107), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFF03A9F4), Color(0xFFFF7A1A), Color(0xFF9C27B0),
)

/** Confete caindo do topo da tela. Não aparece se as animações do sistema estiverem desligadas. */
@Composable
fun BoxScope.Confetti(trigger: Any, durationMillis: Int = 2_400) {
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (animationsOff) return
    val accent = LocalReaderPalette.current.accent
    val particles = remember(trigger) {
        val random = Random(trigger.hashCode())
        List(80) {
            Particle(
                x = random.nextFloat(),
                y = -0.05f - random.nextFloat() * 0.25f,
                vx = (random.nextFloat() - 0.5f) * 0.25f,
                vy = 0.15f + random.nextFloat() * 0.25f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                rotation = random.nextFloat() * 360f,
                width = 6f + random.nextFloat() * 6f,
                height = 10f + random.nextFloat() * 8f,
                color = if (it % 5 == 0) accent else CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(durationMillis, easing = LinearEasing)) }
    Canvas(Modifier.matchParentSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val seconds = t * durationMillis / 1000f
        val density = this.density
        particles.forEach { p ->
            val x = size.width * (p.x + p.vx * seconds)
            val y = size.height * (p.y + p.vy * seconds + 0.18f * seconds * seconds)
            val w = p.width * density
            val h = p.height * density
            rotate(p.rotation + p.spin * seconds, pivot = Offset(x, y)) {
                drawRect(p.color, topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h), alpha = 1f - t * t * t)
            }
        }
    }
}

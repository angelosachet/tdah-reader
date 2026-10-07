package io.github.angelosachet.tdahreader.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.game.Achievement
import io.github.angelosachet.tdahreader.game.Levels
import io.github.angelosachet.tdahreader.ui.theme.LocalReaderPalette
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

fun formatNumber(n: Long): String = NumberFormat.getIntegerInstance(PT_BR).format(n)

fun today(): Long = LocalDate.now().toEpochDay()

/** Plural com o número: no português do Brasil o 0 cai em "one" ("0 dia"), então o zero usa a forma plural. */
@Composable
fun countText(@androidx.annotation.PluralsRes id: Int, count: Int): String =
    pluralStringResource(id, if (count == 0) 2 else count, count)

@Composable
fun rankTitle(level: Int): String = stringArrayResource(R.array.level_ranks).let { it[Levels.rank(level).coerceAtMost(it.lastIndex)] }

/** "3 h 25 min", "12 min", "menos de 1 min". */
@Composable
fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.duration_less_than_minute)
        minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_hours, minutes / 60, minutes % 60)
    }
}

val Achievement.icon: ImageVector
    get() = when (this) {
        Achievement.FIRST_SENTENCE -> Icons.Filled.Flag
        Achievement.SENTENCES_100 -> Icons.Filled.Bolt
        Achievement.SENTENCES_1000 -> Icons.Filled.Speed
        Achievement.SENTENCES_10000 -> Icons.Filled.RocketLaunch
        Achievement.WORDS_100K -> Icons.Filled.Diamond
        Achievement.FIRST_GOAL -> Icons.Filled.TaskAlt
        Achievement.STREAK_3 -> Icons.Filled.LocalFireDepartment
        Achievement.STREAK_7 -> Icons.Filled.Whatshot
        Achievement.STREAK_30 -> Icons.Filled.WorkspacePremium
        Achievement.FIRST_CHAPTER -> Icons.Filled.Bookmark
        Achievement.CHAPTERS_10 -> Icons.Filled.Bookmarks
        Achievement.FIRST_BOOK -> Icons.Filled.EmojiEvents
        Achievement.BOOKS_5 -> Icons.Filled.MilitaryTech
        Achievement.FOCUS_15 -> Icons.Filled.Timer
        Achievement.NIGHT_OWL -> Icons.Filled.NightsStay
        Achievement.EARLY_BIRD -> Icons.Filled.WbSunny
        Achievement.COLLECTOR_3 -> Icons.Filled.CollectionsBookmark
    }

@get:StringRes
val Achievement.titleRes: Int
    get() = when (this) {
        Achievement.FIRST_SENTENCE -> R.string.ach_first_sentence
        Achievement.SENTENCES_100 -> R.string.ach_sentences_100
        Achievement.SENTENCES_1000 -> R.string.ach_sentences_1000
        Achievement.SENTENCES_10000 -> R.string.ach_sentences_10000
        Achievement.WORDS_100K -> R.string.ach_words_100k
        Achievement.FIRST_GOAL -> R.string.ach_first_goal
        Achievement.STREAK_3 -> R.string.ach_streak_3
        Achievement.STREAK_7 -> R.string.ach_streak_7
        Achievement.STREAK_30 -> R.string.ach_streak_30
        Achievement.FIRST_CHAPTER -> R.string.ach_first_chapter
        Achievement.CHAPTERS_10 -> R.string.ach_chapters_10
        Achievement.FIRST_BOOK -> R.string.ach_first_book
        Achievement.BOOKS_5 -> R.string.ach_books_5
        Achievement.FOCUS_15 -> R.string.ach_focus_15
        Achievement.NIGHT_OWL -> R.string.ach_night_owl
        Achievement.EARLY_BIRD -> R.string.ach_early_bird
        Achievement.COLLECTOR_3 -> R.string.ach_collector_3
    }

@get:StringRes
val Achievement.descriptionRes: Int
    get() = when (this) {
        Achievement.FIRST_SENTENCE -> R.string.ach_first_sentence_desc
        Achievement.SENTENCES_100 -> R.string.ach_sentences_100_desc
        Achievement.SENTENCES_1000 -> R.string.ach_sentences_1000_desc
        Achievement.SENTENCES_10000 -> R.string.ach_sentences_10000_desc
        Achievement.WORDS_100K -> R.string.ach_words_100k_desc
        Achievement.FIRST_GOAL -> R.string.ach_first_goal_desc
        Achievement.STREAK_3 -> R.string.ach_streak_3_desc
        Achievement.STREAK_7 -> R.string.ach_streak_7_desc
        Achievement.STREAK_30 -> R.string.ach_streak_30_desc
        Achievement.FIRST_CHAPTER -> R.string.ach_first_chapter_desc
        Achievement.CHAPTERS_10 -> R.string.ach_chapters_10_desc
        Achievement.FIRST_BOOK -> R.string.ach_first_book_desc
        Achievement.BOOKS_5 -> R.string.ach_books_5_desc
        Achievement.FOCUS_15 -> R.string.ach_focus_15_desc
        Achievement.NIGHT_OWL -> R.string.ach_night_owl_desc
        Achievement.EARLY_BIRD -> R.string.ach_early_bird_desc
        Achievement.COLLECTOR_3 -> R.string.ach_collector_3_desc
    }

/** Círculo com o número do nível. */
@Composable
fun LevelBadge(level: Int, size: Dp = 52.dp) {
    val palette = LocalReaderPalette.current
    Box(
        Modifier.size(size).clip(CircleShape).background(palette.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            level.toString(),
            color = palette.onAccent,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** Anel da meta do dia com a chama no meio; a chama acende quando a meta é batida. */
@Composable
fun DailyGoalRing(done: Int, goal: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val met = done >= goal
    val description = stringResource(R.string.goal_ring_description, done.coerceAtMost(goal), goal)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { (done.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.size(30.dp),
            color = if (met) STREAK_ORANGE else palette.accent,
            trackColor = palette.track,
            strokeWidth = 3.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (met) STREAK_ORANGE else palette.secondaryText,
        )
    }
}

/** Chama + número de dias seguidos. */
@Composable
fun StreakFlame(streak: Int, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = if (streak > 0) STREAK_ORANGE else palette.track,
        )
        Text(
            streak.toString(),
            color = if (streak > 0) Color.White else palette.secondaryText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            // O corpo da chama fica na parte de baixo do ícone.
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

val STREAK_ORANGE = Color(0xFFFF7A1A)

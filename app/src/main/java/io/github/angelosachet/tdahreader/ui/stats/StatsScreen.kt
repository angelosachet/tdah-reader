package io.github.angelosachet.tdahreader.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.angelosachet.tdahreader.ui.game.countText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.angelosachet.tdahreader.R
import io.github.angelosachet.tdahreader.data.ReaderSettings
import io.github.angelosachet.tdahreader.game.Achievement
import io.github.angelosachet.tdahreader.game.Levels
import io.github.angelosachet.tdahreader.game.ReadingStats
import io.github.angelosachet.tdahreader.ui.game.LevelBadge
import io.github.angelosachet.tdahreader.ui.game.PT_BR
import io.github.angelosachet.tdahreader.ui.game.STREAK_ORANGE
import io.github.angelosachet.tdahreader.ui.game.StreakFlame
import io.github.angelosachet.tdahreader.ui.game.descriptionRes
import io.github.angelosachet.tdahreader.ui.game.formatDuration
import io.github.angelosachet.tdahreader.ui.game.formatNumber
import io.github.angelosachet.tdahreader.ui.game.icon
import io.github.angelosachet.tdahreader.ui.game.rankTitle
import io.github.angelosachet.tdahreader.ui.game.titleRes
import io.github.angelosachet.tdahreader.ui.game.today
import io.github.angelosachet.tdahreader.ui.theme.LocalReaderPalette
import java.time.LocalDate
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    stats: ReadingStats,
    settings: ReaderSettings,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val today = today()
    // Conquistas já desbloqueadas primeiro, da mais recente para a mais antiga.
    val achievements = Achievement.entries.sortedWith(
        compareByDescending<Achievement> { stats.unlocked[it.name] != null }.thenByDescending { stats.unlocked[it.name] ?: 0L },
    )

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { LevelCard(stats) }
            item(span = { GridItemSpan(maxLineSpan) }) { StreakCard(stats, today) }
            item(span = { GridItemSpan(maxLineSpan) }) { GoalCard(stats, today, settings.dailyGoal, onUpdateSettings) }
            item(span = { GridItemSpan(maxLineSpan) }) { TotalsCard(stats) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.stats_achievements, stats.unlocked.size, Achievement.entries.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            items(achievements, key = { it.name }) { AchievementTile(it, stats) }
        }
    }
}

@Composable
private fun StatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LocalReaderPalette.current.surface)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Bar(progress: Float, color: Color, height: Int = 8) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)),
        color = color,
        trackColor = LocalReaderPalette.current.track,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@Composable
private fun LevelCard(stats: ReadingStats) {
    val palette = LocalReaderPalette.current
    val level = stats.level
    StatsCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(level, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.stats_level, level), style = MaterialTheme.typography.labelLarge, color = palette.secondaryText)
                    Text(rankTitle(level), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.text)
                    Text(
                        stringResource(R.string.stats_total_xp, formatNumber(stats.xp)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.accent,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Bar(Levels.progress(stats.xp), palette.accent, height = 10)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.stats_xp_missing, formatNumber(Levels.xpFor(level + 1) - stats.xp), level + 1),
                style = MaterialTheme.typography.labelMedium,
                color = palette.secondaryText,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.stats_xp_hint), style = MaterialTheme.typography.bodySmall, color = palette.secondaryText)
        }
    }
}

@Composable
private fun StreakCard(stats: ReadingStats, today: Long) {
    val palette = LocalReaderPalette.current
    val streak = stats.streak(today)
    StatsCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakFlame(streak)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        countText(R.plurals.stats_streak, streak),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Text(
                        countText(R.plurals.stats_best_streak, stats.bestStreak),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryText,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            WeekChart(stats, today)
        }
    }
}

/** Frases lidas nos últimos 7 dias; dias com a meta batida ficam em laranja. */
@Composable
private fun WeekChart(stats: ReadingStats, today: Long) {
    val palette = LocalReaderPalette.current
    val days = (6 downTo 0).map { today - it }
    val values = days.map { stats.day(it) }
    val max = values.maxOf { it.sentences }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEachIndexed { i, day ->
            val value = values[i]
            val label = if (day == today) stringResource(R.string.stats_today)
            else LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT, PT_BR).trimEnd('.')
            val description = stringResource(R.string.stats_day_description, label, value.sentences)
            Column(
                Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (value.sentences > 0) value.sentences.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height((60f * value.sentences / max).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                value.goalMet -> STREAK_ORANGE
                                value.sentences > 0 -> palette.accent
                                else -> palette.track
                            },
                        ),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) palette.text else palette.secondaryText,
                    fontWeight = if (day == today) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    stats: ReadingStats,
    today: Long,
    goal: Int,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val done = stats.day(today).sentences
    StatsCard {
        Column {
            Text(stringResource(R.string.stats_daily_goal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.text)
            Spacer(Modifier.height(4.dp))
            Text(
                if (done >= goal) stringResource(R.string.goal_today_done, goal) else stringResource(R.string.goal_today_progress, done, goal),
                style = MaterialTheme.typography.bodyMedium,
                color = if (done >= goal) STREAK_ORANGE else palette.secondaryText,
            )
            Spacer(Modifier.height(8.dp))
            Bar(done.toFloat() / goal.coerceAtLeast(1), if (done >= goal) STREAK_ORANGE else palette.accent)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderSettings.GOAL_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = option == goal,
                        onClick = { onUpdateSettings { it.copy(dailyGoal = option) } },
                        label = { Text(stringResource(R.string.stats_goal_option, option)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accent,
                            selectedLabelColor = palette.onAccent,
                        ),
                    )
                }
            }
            Text(stringResource(R.string.stats_goal_hint), style = MaterialTheme.typography.bodySmall, color = palette.secondaryText)
        }
    }
}

@Composable
private fun TotalsCard(stats: ReadingStats) {
    StatsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Total(formatNumber(stats.sentences), stringResource(R.string.stats_total_sentences), Modifier.weight(1f))
                Total(formatNumber(stats.words), stringResource(R.string.stats_total_words), Modifier.weight(1f))
            }
            Row {
                Total(formatDuration(stats.millis), stringResource(R.string.stats_total_time), Modifier.weight(1f))
                Total(stats.finishedBooks.size.toString(), stringResource(R.string.stats_total_books), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Total(value: String, label: String, modifier: Modifier) {
    val palette = LocalReaderPalette.current
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.text)
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.secondaryText)
    }
}

@Composable
private fun AchievementTile(achievement: Achievement, stats: ReadingStats) {
    val palette = LocalReaderPalette.current
    val unlocked = stats.unlocked[achievement.name] != null
    StatsCard(Modifier.height(176.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) palette.accent else palette.track),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (unlocked) achievement.icon else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (unlocked) palette.onAccent else palette.secondaryText,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(achievement.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) palette.text else palette.secondaryText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(achievement.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!unlocked && achievement.target > 1) {
                Spacer(Modifier.weight(1f))
                Bar(achievement.current(stats).toFloat() / achievement.target, palette.accent, height = 6)
                Text(
                    "${formatNumber(achievement.current(stats))} / ${formatNumber(achievement.target)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                )
            }
        }
    }
}

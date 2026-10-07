package io.github.angelosachet.tdahreader.game

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.ZoneId

/** O que foi lido em um dia. */
@Serializable
data class DayStats(
    val sentences: Int = 0,
    val words: Int = 0,
    val millis: Long = 0,
    /** A meta do dia foi batida (fica marcado mesmo que a meta mude depois). */
    val goalMet: Boolean = false,
)

/** Todo o progresso de leitura, somando todos os livros. */
@Serializable
data class ReadingStats(
    val xp: Long = 0,
    val sentences: Long = 0,
    val words: Long = 0,
    val millis: Long = 0,
    val chapters: Int = 0,
    val finishedBooks: Set<String> = emptySet(),
    /** Dia ([java.time.LocalDate.toEpochDay]) -> leitura do dia. */
    val days: Map<Long, DayStats> = emptyMap(),
    val bestStreak: Int = 0,
    val bestSessionMillis: Long = 0,
    val readLateNight: Boolean = false,
    val readEarlyMorning: Boolean = false,
    /** Maior número de livros que já esteve na estante. */
    val librarySize: Int = 0,
    /** Conquista ([Achievement.name]) -> quando foi desbloqueada. */
    val unlocked: Map<String, Long> = emptyMap(),
) {
    val level: Int get() = Levels.levelFor(xp)

    fun day(epochDay: Long): DayStats = days[epochDay] ?: DayStats()

    /** Dias seguidos com a meta batida, terminando hoje (ou ontem, se hoje ainda não bateu). */
    fun streak(today: Long): Int {
        var day = if (days[today]?.goalMet == true) today else today - 1
        var count = 0
        while (days[day]?.goalMet == true) {
            count++
            day--
        }
        return count
    }
}

/** Cada palavra lida vale 1 XP; cada nível pede 500 XP a mais que o anterior. */
object Levels {
    private const val STEP = 500L

    /** Nível a partir do qual cada título vale (o título é o da maior faixa alcançada). */
    val RANKS = listOf(1, 3, 5, 8, 12, 17, 23, 30)

    /** XP total para chegar ao [level] (o nível 1 começa em 0). */
    fun xpFor(level: Int): Long = STEP * (level - 1) * level / 2

    fun levelFor(xp: Long): Int {
        var level = 1
        while (xpFor(level + 1) <= xp) level++
        return level
    }

    /** Quanto do caminho até o próximo nível já foi feito (0 a 1). */
    fun progress(xp: Long): Float {
        val level = levelFor(xp)
        val from = xpFor(level)
        return ((xp - from).toFloat() / (xpFor(level + 1) - from)).coerceIn(0f, 1f)
    }

    fun rank(level: Int): Int = RANKS.indexOfLast { level >= it }.coerceAtLeast(0)
}

enum class Achievement(val target: Long, private val metric: (ReadingStats) -> Long) {
    FIRST_SENTENCE(1, { it.sentences }),
    SENTENCES_100(100, { it.sentences }),
    SENTENCES_1000(1_000, { it.sentences }),
    SENTENCES_10000(10_000, { it.sentences }),
    WORDS_100K(100_000, { it.words }),
    FIRST_GOAL(1, { s -> s.days.values.count { it.goalMet }.toLong() }),
    STREAK_3(3, { it.bestStreak.toLong() }),
    STREAK_7(7, { it.bestStreak.toLong() }),
    STREAK_30(30, { it.bestStreak.toLong() }),
    FIRST_CHAPTER(1, { it.chapters.toLong() }),
    CHAPTERS_10(10, { it.chapters.toLong() }),
    FIRST_BOOK(1, { it.finishedBooks.size.toLong() }),
    BOOKS_5(5, { it.finishedBooks.size.toLong() }),
    FOCUS_15(15, { it.bestSessionMillis / 60_000 }),
    NIGHT_OWL(1, { if (it.readLateNight) 1 else 0 }),
    EARLY_BIRD(1, { if (it.readEarlyMorning) 1 else 0 }),
    COLLECTOR_3(3, { it.librarySize.toLong() }),
    ;

    /** Progresso atual, limitado ao alvo (para barras de "faltam X"). */
    fun current(stats: ReadingStats): Long = metric(stats).coerceIn(0, target)

    fun isReached(stats: ReadingStats): Boolean = metric(stats) >= target
}

/** Algo que merece comemoração na tela. */
sealed interface GameEvent {
    data class GoalReached(val streak: Int) : GameEvent
    data class LevelUp(val level: Int) : GameEvent
    data class Unlocked(val achievement: Achievement) : GameEvent
    data class ChapterDone(val xp: Int) : GameEvent
    data class BookDone(val xp: Int) : GameEvent
}

class Update(val stats: ReadingStats, val events: List<GameEvent>)

/** Regras da gamificação: funções puras que recebem o progresso e devolvem o novo, com os eventos. */
object Progression {
    const val GOAL_XP = 100
    const val CHAPTER_XP = 50
    const val BOOK_XP = 500

    fun sentenceRead(
        stats: ReadingStats,
        words: Int,
        millis: Long,
        sessionMillis: Long,
        dailyGoal: Int,
        now: LocalDateTime,
    ): Update {
        val today = now.toLocalDate().toEpochDay()
        val before = stats.day(today)
        var day = before.copy(sentences = before.sentences + 1, words = before.words + words, millis = before.millis + millis)
        var xp = stats.xp + words
        val reachedGoal = !before.goalMet && day.sentences >= dailyGoal.coerceAtLeast(1)
        if (reachedGoal) {
            day = day.copy(goalMet = true)
            xp += GOAL_XP
        }
        var next = stats.copy(
            xp = xp,
            sentences = stats.sentences + 1,
            words = stats.words + words,
            millis = stats.millis + millis,
            days = stats.days + (today to day),
            bestSessionMillis = maxOf(stats.bestSessionMillis, sessionMillis),
            readLateNight = stats.readLateNight || now.hour in 0..3,
            readEarlyMorning = stats.readEarlyMorning || now.hour in 5..6,
        )
        val events = mutableListOf<GameEvent>()
        if (reachedGoal) {
            val streak = next.streak(today)
            next = next.copy(bestStreak = maxOf(next.bestStreak, streak))
            events += GameEvent.GoalReached(streak)
        }
        return finish(stats, next, events, now)
    }

    fun chapterFinished(stats: ReadingStats, now: LocalDateTime): Update = finish(
        stats,
        stats.copy(xp = stats.xp + CHAPTER_XP, chapters = stats.chapters + 1),
        listOf(GameEvent.ChapterDone(CHAPTER_XP)),
        now,
    )

    /** Só a primeira vez que o livro é terminado conta. */
    fun bookFinished(stats: ReadingStats, bookId: String, now: LocalDateTime): Update {
        if (bookId in stats.finishedBooks) return Update(stats, emptyList())
        return finish(
            stats,
            stats.copy(xp = stats.xp + BOOK_XP, finishedBooks = stats.finishedBooks + bookId),
            listOf(GameEvent.BookDone(BOOK_XP)),
            now,
        )
    }

    fun librarySize(stats: ReadingStats, size: Int, now: LocalDateTime): Update {
        if (size <= stats.librarySize) return Update(stats, emptyList())
        return finish(stats, stats.copy(librarySize = size), emptyList(), now)
    }

    /** Acrescenta subida de nível e conquistas novas aos eventos. */
    private fun finish(old: ReadingStats, new: ReadingStats, events: List<GameEvent>, now: LocalDateTime): Update {
        val all = events.toMutableList()
        if (new.level > old.level) all += GameEvent.LevelUp(new.level)
        val reached = Achievement.entries.filter { it.name !in new.unlocked && it.isReached(new) }
        if (reached.isEmpty()) return Update(new, all)
        val stamp = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        all += reached.map(GameEvent::Unlocked)
        return Update(new.copy(unlocked = new.unlocked + reached.associate { it.name to stamp }), all)
    }
}

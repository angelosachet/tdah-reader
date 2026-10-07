package io.github.angelosachet.tdahreader.game

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProgressionTest {

    private val noon = LocalDateTime.of(2026, 10, 7, 12, 0)

    private fun read(stats: ReadingStats, times: Int, words: Int = 10, goal: Int = 30, at: LocalDateTime = noon): Pair<ReadingStats, List<GameEvent>> {
        var s = stats
        val events = mutableListOf<GameEvent>()
        repeat(times) {
            val update = Progression.sentenceRead(s, words, 3_000, 0, goal, at)
            s = update.stats
            events += update.events
        }
        return s to events
    }

    @Test
    fun `niveis pedem cada vez mais xp`() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(1, Levels.levelFor(499))
        assertEquals(2, Levels.levelFor(500))
        assertEquals(3, Levels.levelFor(1_500))
        assertEquals(0.5f, Levels.progress(1_000), 0.001f)
        assertEquals(0, Levels.rank(1))
        assertEquals(1, Levels.rank(4))
        assertEquals(Levels.RANKS.lastIndex, Levels.rank(99))
    }

    @Test
    fun `cada palavra vale um xp e a meta da bonus uma vez por dia`() {
        val (stats, events) = read(ReadingStats(), times = 35)
        assertEquals(35L, stats.sentences)
        assertEquals(350L + Progression.GOAL_XP, stats.xp)
        assertEquals(1, events.count { it is GameEvent.GoalReached })
        assertTrue(stats.day(noon.toLocalDate().toEpochDay()).goalMet)
    }

    @Test
    fun `sequencia conta dias seguidos com a meta batida`() {
        var stats = ReadingStats()
        for (offset in 0L..2L) stats = read(stats, times = 30, at = noon.plusDays(offset)).first
        val third = noon.plusDays(2).toLocalDate().toEpochDay()
        assertEquals(3, stats.streak(third))
        assertEquals(3, stats.bestStreak)
        // No dia seguinte, antes de bater a meta, a sequência ainda vale.
        assertEquals(3, stats.streak(third + 1))
        // Pulou um dia inteiro: zera.
        assertEquals(0, stats.streak(third + 2))
        assertTrue(Achievement.STREAK_3.name in stats.unlocked)
    }

    @Test
    fun `conquistas sao desbloqueadas uma vez so`() {
        val (stats, events) = read(ReadingStats(), times = 2)
        val unlocked = events.filterIsInstance<GameEvent.Unlocked>().map { it.achievement }
        assertEquals(listOf(Achievement.FIRST_SENTENCE), unlocked)
        assertEquals(setOf(Achievement.FIRST_SENTENCE.name), stats.unlocked.keys)
    }

    @Test
    fun `livro terminado so da bonus na primeira vez`() {
        val first = Progression.bookFinished(ReadingStats(), "abc", noon)
        assertEquals(Progression.BOOK_XP.toLong(), first.stats.xp)
        assertTrue(first.events.any { it is GameEvent.BookDone })
        assertTrue(first.events.any { it is GameEvent.Unlocked && it.achievement == Achievement.FIRST_BOOK })
        val again = Progression.bookFinished(first.stats, "abc", noon)
        assertEquals(first.stats, again.stats)
        assertTrue(again.events.isEmpty())
    }

    @Test
    fun `subir de nivel gera evento`() {
        val update = Progression.sentenceRead(ReadingStats(xp = 495), 10, 1_000, 0, 30, noon)
        assertTrue(update.events.contains(GameEvent.LevelUp(2)))
    }

    @Test
    fun `horario da leitura libera coruja e madrugador`() {
        val night = read(ReadingStats(), times = 1, at = noon.withHour(1)).first
        assertTrue(Achievement.NIGHT_OWL.name in night.unlocked)
        assertFalse(Achievement.EARLY_BIRD.name in night.unlocked)
        val morning = read(night, times = 1, at = noon.withHour(6)).first
        assertTrue(Achievement.EARLY_BIRD.name in morning.unlocked)
    }

    @Test
    fun `progresso sobrevive a gravacao em json`() {
        val stats = read(ReadingStats(finishedBooks = setOf("abc")), times = 31).first
        val json = Json.encodeToString(ReadingStats.serializer(), stats)
        assertEquals(stats, Json.decodeFromString(ReadingStats.serializer(), json))
    }
}

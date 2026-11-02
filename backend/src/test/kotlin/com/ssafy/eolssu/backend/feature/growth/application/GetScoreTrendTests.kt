package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.PlayedSong
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreScope
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreTrendRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoredPlay
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GetScoreTrendTests {
    private val repository = InMemoryScoreTrendRepository()
    private val lookups = mutableListOf<Pair<Long, Int>>()

    /** 지금 악보: 버전 2, 음표 8개가 각 0~3에 두 개씩. */
    private val trend = GetScoreTrend(repository) { songId, version ->
        lookups += songId to version
        if (version == 2) listOf(0, 0, 1, 1, 2, 2, 3, 3) else null
    }

    @Test
    fun `shows the most recently played song and its latest plays oldest first`() {
        repository.songs += listOf(song(DORAJI), song(ARIRANG))
        (1L..7L).forEach { repository.add(DORAJI, ScoreScope.FULL, play(sessionId = it, minute = it)) }
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 99, minute = 99))

        val result = trend.trend(USER, songId = null, scope = ScoreScope.FULL, limit = 5)

        assertEquals(DORAJI, result.songId)
        assertEquals(ScoreScope.FULL, result.scope)
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), result.points.map { it.play.sessionId })
        assertEquals(repository.songs, result.songs)
    }

    @Test
    fun `keeps the session order of plays that started at the same time`() {
        repository.songs += song(ARIRANG)
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 11, minute = 5))
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 10, minute = 5))
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 12, minute = 4))

        val result = trend.trend(USER, songId = ARIRANG, scope = ScoreScope.FULL, limit = 2)

        assertEquals(listOf(10L, 11L), result.points.map { it.play.sessionId })
    }

    @Test
    fun `looks only at the requested scope`() {
        repository.songs += song(ARIRANG)
        repository.add(ARIRANG, ScoreScope.SECTION, play(sessionId = 1, minute = 1, start = 2, end = 5))

        assertEquals(listOf(1L), trend.trend(USER, ARIRANG, ScoreScope.SECTION, 5).points.map { it.play.sessionId })
        assertEquals(emptyList(), trend.trend(USER, ARIRANG, ScoreScope.FULL, 5).points)
    }

    @Test
    fun `answers a song that was never played with no points`() {
        repository.songs += song(ARIRANG)

        val result = trend.trend(USER, songId = 404, scope = ScoreScope.FULL, limit = 5)

        assertEquals(404L, result.songId)
        assertEquals(emptyList(), result.points)
        assertFalse(result.comparable)
        assertEquals(repository.songs, result.songs)
        assertEquals(emptyList(), lookups)
    }

    @Test
    fun `has nothing to show before any counted play`() {
        val result = trend.trend(USER, songId = null, scope = ScoreScope.SECTION, limit = 5)

        assertEquals(ScoreTrend(null, ScoreScope.SECTION, emptyList(), false, emptyList()), result)
    }

    @Test
    fun `is comparable only with two or more plays under the same conditions`() {
        repository.songs += song(ARIRANG)
        assertFalse(trend.trend(USER, ARIRANG, ScoreScope.FULL, 5).comparable) // 0건

        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 1, minute = 1))
        assertFalse(trend.trend(USER, ARIRANG, ScoreScope.FULL, 5).comparable) // 1건

        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 2, minute = 2))
        assertTrue(trend.trend(USER, ARIRANG, ScoreScope.FULL, 5).comparable)
    }

    @Test
    fun `is not comparable when the tempo, its unit, the score version or the scoring model differ`() {
        val variants = mapOf(
            "BPM" to play(sessionId = 2, minute = 2, bpm = 80),
            "BPM 단위" to play(sessionId = 2, minute = 2, bpmUnit = "DOTTED_QUARTER"),
            "악보 버전" to play(sessionId = 2, minute = 2, scoreVersion = 1),
            "채점 모델" to play(sessionId = 2, minute = 2, modelVersion = "pyin-3"),
        )
        for ((condition, variant) in variants) {
            val plays = InMemoryScoreTrendRepository()
            plays.add(ARIRANG, ScoreScope.FULL, play(sessionId = 1, minute = 1))
            plays.add(ARIRANG, ScoreScope.FULL, variant)

            assertFalse(GetScoreTrend(plays) { _, _ -> null }.trend(USER, ARIRANG, ScoreScope.FULL, 5).comparable, condition)
        }
    }

    @Test
    fun `judges only the plays it shows`() {
        repository.songs += song(ARIRANG)
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 1, minute = 1, bpm = 60)) // limit 밖
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 2, minute = 2))
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 3, minute = 3))

        assertTrue(trend.trend(USER, ARIRANG, ScoreScope.FULL, 2).comparable)
        assertFalse(trend.trend(USER, ARIRANG, ScoreScope.FULL, 3).comparable)
    }

    @Test
    fun `needs the same range to compare section plays`() {
        repository.songs += song(ARIRANG)
        repository.add(ARIRANG, ScoreScope.SECTION, play(sessionId = 1, minute = 1, start = 2, end = 3))
        repository.add(ARIRANG, ScoreScope.SECTION, play(sessionId = 2, minute = 2, start = 2, end = 3))
        assertTrue(trend.trend(USER, ARIRANG, ScoreScope.SECTION, 5).comparable)

        repository.add(ARIRANG, ScoreScope.SECTION, play(sessionId = 3, minute = 3, start = 2, end = 5))
        assertFalse(trend.trend(USER, ARIRANG, ScoreScope.SECTION, 5).comparable)
    }

    @Test
    fun `adds the columns only for plays on the current score version`() {
        repository.songs += song(ARIRANG)
        repository.add(ARIRANG, ScoreScope.SECTION, play(sessionId = 1, minute = 1, start = 2, end = 5, scoreVersion = 1))
        repository.add(ARIRANG, ScoreScope.SECTION, play(sessionId = 2, minute = 2, start = 2, end = 5))
        repository.add(ARIRANG, ScoreScope.SECTION, play(sessionId = 3, minute = 3, start = 0, end = 7))

        val points = trend.trend(USER, ARIRANG, ScoreScope.SECTION, 5).points

        assertEquals(listOf(null, ColumnRange(1, 2), ColumnRange(0, 3)), points.map { it.columns })
        assertEquals(listOf(ARIRANG to 1, ARIRANG to 2), lookups) // 악보 버전마다 한 번
    }

    @Test
    fun `leaves the columns empty when the range is outside the current score`() {
        repository.songs += song(ARIRANG)
        repository.add(ARIRANG, ScoreScope.FULL, play(sessionId = 1, minute = 1, start = 0, end = 8)) // 지금 악보는 음표 0~7

        assertNull(trend.trend(USER, ARIRANG, ScoreScope.FULL, 5).points.single().columns)
    }

    private fun song(songId: Long) = PlayedSong(songId, "곡 $songId", Instant.parse("2026-10-30T00:00:00Z"), 1, 0)

    private fun play(
        sessionId: Long,
        minute: Long,
        bpm: Int = 72,
        bpmUnit: String = "QUARTER",
        start: Int = 0,
        end: Int = 7,
        scoreVersion: Int = 2,
        modelVersion: String = "pyin-4",
    ) = ScoredPlay(
        sessionId, BASE.plusSeconds(minute * 60), bpm, bpmUnit, start, end, scoreVersion, modelVersion,
        totalScore = 70, pitchScore = 71, timingScore = 72, durationScore = 73,
    )

    /** 저장소 계약대로 최근 순(같은 시각이면 세션 ID가 큰 것 먼저)으로 limit개를 준다. */
    private class InMemoryScoreTrendRepository : ScoreTrendRepository {
        val songs = mutableListOf<PlayedSong>()
        private val plays = mutableMapOf<Pair<Long, ScoreScope>, MutableList<ScoredPlay>>()

        fun add(songId: Long, scope: ScoreScope, play: ScoredPlay) {
            plays.getOrPut(songId to scope) { mutableListOf() } += play
        }

        override fun playedSongs(userId: Long) = if (userId == USER) songs.toList() else emptyList()

        override fun recentScoredPlays(userId: Long, songId: Long, scope: ScoreScope, limit: Int) =
            plays[songId to scope].orEmpty()
                .filter { userId == USER }
                .sortedWith(compareByDescending<ScoredPlay> { it.startedAt }.thenByDescending { it.sessionId })
                .take(limit)
    }

    private companion object {
        const val USER = 1L
        const val ARIRANG = 1L
        const val DORAJI = 2L
        val BASE: Instant = Instant.parse("2026-10-01T03:00:00Z")
    }
}

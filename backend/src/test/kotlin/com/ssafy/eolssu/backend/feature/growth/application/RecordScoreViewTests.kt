package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewSegment
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewViolation
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class RecordScoreViewTests {
    private val startedAt = Instant.parse("2026-10-27T05:00:00Z")
    private val segment = ScoreViewSegment(1, 2, "key-1", startedAt, startedAt.plusSeconds(300), 300, 240)
    private val repository = InMemoryScoreViewRepository(songIds = setOf(2))
    private val service = RecordScoreView(repository, Clock.fixed(startedAt.plusSeconds(301), ZoneOffset.UTC))

    @Test
    fun `stores a new key once`() {
        assertEquals(RecordScoreViewResult.Created(segment), service.record(segment))
        assertEquals(listOf(segment), repository.rows)
    }

    @Test
    fun `returns the stored segment for the same key and body without storing again`() {
        service.record(segment)
        assertEquals(RecordScoreViewResult.AlreadyRecorded(segment), service.record(segment.copy(startedAt = startedAt.plusNanos(1))))
        assertEquals(1, repository.rows.size)
    }

    @Test
    fun `rejects the same key with a different body`() {
        service.record(segment)
        assertEquals(RecordScoreViewResult.Conflict, service.record(segment.copy(countedSeconds = 200)))
        assertEquals(listOf(segment), repository.rows)
    }

    @Test
    fun `keeps keys separate per user`() {
        service.record(segment)
        assertIs<RecordScoreViewResult.Created>(service.record(segment.copy(userId = 9)))
        assertEquals(2, repository.rows.size)
    }

    @Test
    fun `rejects an invalid segment before touching storage`() {
        assertEquals(
            RecordScoreViewResult.Invalid(ScoreViewViolation.COUNTED_EXCEEDS_VISIBLE),
            service.record(segment.copy(countedSeconds = 301)),
        )
        assertEquals(0, repository.calls)
    }

    @Test
    fun `reports a missing song`() {
        assertEquals(RecordScoreViewResult.SongNotFound, service.record(segment.copy(songId = 99)))
        assertEquals(emptyList(), repository.rows)
    }

    @Test
    fun `compares with the row a concurrent request stored first`() {
        repository.storeBeforeNextInsert = segment
        assertEquals(RecordScoreViewResult.AlreadyRecorded(segment), service.record(segment))

        val racing = InMemoryScoreViewRepository(songIds = setOf(2)).apply { storeBeforeNextInsert = segment.copy(countedSeconds = 1) }
        assertEquals(RecordScoreViewResult.Conflict, RecordScoreView(racing, Clock.fixed(startedAt.plusSeconds(301), ZoneOffset.UTC)).record(segment))
        assertEquals(1, racing.rows.size)
    }

    private class InMemoryScoreViewRepository(private val songIds: Set<Long>) : ScoreViewRepository {
        val rows = mutableListOf<ScoreViewSegment>()
        var calls = 0
        var storeBeforeNextInsert: ScoreViewSegment? = null

        override fun songExists(songId: Long): Boolean {
            calls++
            return songId in songIds
        }

        override fun findByKey(userId: Long, idempotencyKey: String): ScoreViewSegment? {
            calls++
            return rows.find { it.userId == userId && it.idempotencyKey == idempotencyKey }
        }

        override fun insertIfAbsent(segment: ScoreViewSegment): Boolean {
            calls++
            storeBeforeNextInsert?.let { rows += it; storeBeforeNextInsert = null }
            if (findByKey(segment.userId, segment.idempotencyKey) != null) return false
            rows += segment
            return true
        }
    }
}

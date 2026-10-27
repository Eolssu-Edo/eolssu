package com.ssafy.eolssu.backend.feature.growth.domain

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ScoreViewSegmentTests {
    private val startedAt = Instant.parse("2026-10-27T05:00:00Z")
    private val segment = ScoreViewSegment(
        userId = 1,
        songId = 2,
        idempotencyKey = "4f1c2b8e-0d7a-4c1e-9a55-0b6f1e2d3c4b",
        startedAt = startedAt,
        endedAt = startedAt.plusSeconds(300),
        visibleSeconds = 300,
        countedSeconds = 240,
    )
    private val receivedAt = startedAt.plusSeconds(301)

    @Test
    fun `accepts a closed segment received after it ended`() {
        assertNull(segment.violation(receivedAt))
    }

    @Test
    fun `rejects negative seconds and counted time above visible time`() {
        assertEquals(ScoreViewViolation.NEGATIVE_SECONDS, segment.copy(visibleSeconds = -1, countedSeconds = 0).violation(receivedAt))
        assertEquals(ScoreViewViolation.NEGATIVE_SECONDS, segment.copy(countedSeconds = -1).violation(receivedAt))
        assertEquals(ScoreViewViolation.COUNTED_EXCEEDS_VISIBLE, segment.copy(countedSeconds = 301).violation(receivedAt))
    }

    @Test
    fun `rejects a segment that ends before it starts`() {
        assertEquals(
            ScoreViewViolation.ENDED_BEFORE_STARTED,
            segment.copy(endedAt = startedAt.minusMillis(1), visibleSeconds = 0, countedSeconds = 0).violation(receivedAt),
        )
    }

    @Test
    fun `allows one second of rounding over the segment length and no more`() {
        assertNull(segment.copy(visibleSeconds = 301).violation(receivedAt.plusSeconds(10)))
        assertEquals(
            ScoreViewViolation.VISIBLE_EXCEEDS_PERIOD,
            segment.copy(endedAt = startedAt.plusMillis(299_999), visibleSeconds = 301).violation(receivedAt.plusSeconds(10)),
        )
    }

    @Test
    fun `rejects visible time longer than the time since it started on the server clock`() {
        val sentRightAway = segment.copy(visibleSeconds = 301)
        assertNull(sentRightAway.violation(startedAt.plusSeconds(300)))
        assertEquals(ScoreViewViolation.VISIBLE_EXCEEDS_ELAPSED, sentRightAway.violation(startedAt.plusMillis(299_999)))
        assertEquals(ScoreViewViolation.VISIBLE_EXCEEDS_ELAPSED, segment.violation(startedAt.minusSeconds(3600)))
    }

    @Test
    fun `requires a printable ASCII key of at most 100 characters`() {
        assertNull(segment.copy(idempotencyKey = "k".repeat(100)).violation(receivedAt))
        listOf("", " ", "k".repeat(101), "키", "a b").forEach {
            assertEquals(ScoreViewViolation.INVALID_KEY, segment.copy(idempotencyKey = it).violation(receivedAt), it)
        }
    }

    @Test
    fun `compares content at stored microsecond precision`() {
        val sent = segment.copy(startedAt = startedAt.plusNanos(123_456_789))
        val stored = sent.truncatedToStoredPrecision()
        assertEquals(startedAt.plusNanos(123_456_000), stored.startedAt)
        assertTrue(stored.sameContentAs(sent.truncatedToStoredPrecision()))
        assertFalse(stored.sameContentAs(stored.copy(countedSeconds = 239)))
        assertFalse(stored.sameContentAs(stored.copy(songId = 3)))
    }
}

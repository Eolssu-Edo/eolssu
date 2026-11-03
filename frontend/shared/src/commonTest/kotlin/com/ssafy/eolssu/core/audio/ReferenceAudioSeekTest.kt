package com.ssafy.eolssu.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReferenceAudioSeekTest {
    @Test fun playingAndPausedKeepTheirStatusAndIdentityAtTheRequestedPosition() {
        for (status in listOf(AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused)) {
            val current = AudioPlaybackState("clip-a", status, 750, 5_000)
            assertEquals(current.copy(positionMs = 2_125), current.referenceSeekTarget(2_125))
        }
    }

    @Test fun completedClipCanBePositionedForAnExplicitResume() {
        val completed = AudioPlaybackState("clip-a", AudioPlaybackStatus.Completed, 5_000, 5_000)
        assertEquals(
            AudioPlaybackState("clip-a", AudioPlaybackStatus.Paused, 1_500, 5_000),
            completed.referenceSeekTarget(1_500),
        )
    }

    @Test fun positionsOutsideTheDecodedDurationClampWithoutOverflow() {
        val current = AudioPlaybackState("clip-a", AudioPlaybackStatus.Paused, 750, 5_000)
        assertEquals(0L, current.referenceSeekTarget(Long.MIN_VALUE)?.positionMs)
        assertEquals(5_000L, current.referenceSeekTarget(Long.MAX_VALUE)?.positionMs)
        assertEquals(0L, current.referenceSeekTarget(0)?.positionMs)
        assertEquals(5_000L, current.referenceSeekTarget(5_000)?.positionMs)
    }

    @Test fun unpreparedAndInterruptedSourcesCannotBeSeeked() {
        for (status in listOf(AudioPlaybackStatus.Idle, AudioPlaybackStatus.Loading,
            AudioPlaybackStatus.Failed, AudioPlaybackStatus.Unavailable, AudioPlaybackStatus.Interrupted)) {
            assertNull(AudioPlaybackState("clip-a", status, 750, 5_000).referenceSeekTarget(2_000))
        }
        assertNull(AudioPlaybackState(null, AudioPlaybackStatus.Playing, 750, 5_000).referenceSeekTarget(2_000))
        assertNull(AudioPlaybackState("clip-a", AudioPlaybackStatus.Playing, 0, 0).referenceSeekTarget(2_000))
        assertNull(AudioPlaybackState("clip-a", AudioPlaybackStatus.Paused, 0, -1).referenceSeekTarget(2_000))
    }
}

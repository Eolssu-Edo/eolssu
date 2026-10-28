package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.audio.RecordingSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class RecordingClockTest {

    private val time = TestTimeSource()
    private val clock = RecordingClock(time)

    @Test
    fun pausedTimeIsNotCounted() {
        clock.start()
        time += 30.seconds
        clock.pause()
        time += 2.minutes // 연주 중단 확인 창
        clock.resume()
        time += 10.seconds
        assertEquals(40.seconds, clock.elapsed())
    }

    @Test
    fun reachesLimitOnlyAfterTenMinutesOfRecording() {
        clock.start()
        time += 9.minutes + 59.seconds
        clock.pause()
        time += 5.minutes
        clock.resume()
        assertTrue(clock.elapsed() < RecordingSpec.MaxDuration)
        time += 1.seconds
        assertTrue(clock.elapsed() >= RecordingSpec.MaxDuration)
    }

    @Test
    fun startAndResetBeginFromZero() {
        clock.start()
        time += 1.minutes
        clock.start()
        assertEquals(Duration.ZERO, clock.elapsed())
        time += 5.seconds
        clock.reset()
        assertFalse(clock.running)
        assertEquals(Duration.ZERO, clock.elapsed())
    }

    @Test
    fun recordingOverTwentyMebibytesIsTooLarge() {
        assertFalse(RecordedAudio("a.m4a", RecordingSpec.MAX_BYTES).tooLarge)
        assertTrue(RecordedAudio("a.m4a", RecordingSpec.MAX_BYTES + 1).tooLarge)
    }
}

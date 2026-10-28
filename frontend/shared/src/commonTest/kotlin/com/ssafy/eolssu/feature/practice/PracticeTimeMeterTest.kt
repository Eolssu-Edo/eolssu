package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class PracticeTimeMeterTest {

    private val time = TestTimeSource()
    private val origin = time.markNow()
    private var keys = 0
    private val meter = PracticeTimeMeter(
        now = { WALL_START + origin.elapsedNow().inWholeMilliseconds },
        timeSource = time,
        newKey = { "key-${++keys}" },
    )

    private fun show() = meter.show(songId = "12")

    @Test
    fun fiveMinutesOnScreenCountsFiveMinutes() {
        show()
        time += 5.minutes
        val segment = assertNotNull(meter.hide())
        assertEquals(300, segment.visibleSeconds)
        assertEquals(300, segment.countedSeconds)
        assertEquals(WALL_START, segment.startedAtMillis)
        assertEquals(WALL_START + 300_000, segment.endedAtMillis)
        assertEquals("12", segment.songId)
    }

    @Test
    fun backgroundSplitsSegmentsAndIsNotCounted() {
        show()
        time += 90.seconds
        val first = assertNotNull(meter.hide())
        time += 2.minutes // 백그라운드
        assertNull(meter.snapshot())
        show()
        time += 90.seconds
        val second = assertNotNull(meter.hide())

        assertNotEquals(first.key, second.key)
        assertEquals(180, first.countedSeconds + second.countedSeconds)
        assertEquals(WALL_START + 210_000, second.startedAtMillis)
    }

    @Test
    fun remindsOnceAtNineThirtyAndStopsCountingAtTenMinutes() {
        show()
        assertEquals(PracticeTimeMeter.ReminderAt, meter.untilReminder())
        time += 9.minutes + 29.seconds
        assertFalse(meter.takeReminder())
        time += 1.seconds
        assertTrue(meter.takeReminder())
        assertFalse(meter.takeReminder())
        assertNull(meter.untilReminder())

        time += 20.minutes // 응답 없음
        val segment = assertNotNull(meter.hide())
        assertEquals(29 * 60 + 30, segment.visibleSeconds)
        assertEquals(10 * 60, segment.countedSeconds)
    }

    @Test
    fun answeringReminderCountsAgainForTenMinutes() {
        show()
        time += 9.minutes + 30.seconds
        assertTrue(meter.takeReminder())
        time += 10.seconds
        meter.onActivity() // 알림에 응답
        assertEquals(PracticeTimeMeter.ReminderAt, meter.untilReminder())

        time += 15.minutes
        val segment = assertNotNull(meter.hide())
        assertEquals(9 * 60 + 40 + 10 * 60, segment.countedSeconds)
    }

    @Test
    fun activityAfterIdleLimitResumesCounting() {
        show()
        time += 15.minutes
        meter.onActivity()
        time += 1.minutes
        assertEquals(11 * 60, assertNotNull(meter.hide()).countedSeconds)
    }

    @Test
    fun runningActivityCountsWithoutInput() {
        show()
        meter.setRunning(ContinuousActivity.PLAYBACK, true)
        meter.setRunning(ContinuousActivity.METRONOME, true)
        assertNull(meter.untilReminder())
        time += 20.minutes
        assertFalse(meter.takeReminder())

        meter.setRunning(ContinuousActivity.PLAYBACK, false)
        time += 5.minutes // 메트로놈만 돌아감
        meter.setRunning(ContinuousActivity.METRONOME, false)
        assertEquals(PracticeTimeMeter.ReminderAt, meter.untilReminder())
        time += 15.minutes // 멈춘 뒤 10분까지 인정

        assertEquals(35 * 60, assertNotNull(meter.hide()).countedSeconds)
    }

    @Test
    fun recordingStartedAfterIdleLimitCountsFromStart() {
        show()
        time += 12.minutes
        meter.setRunning(ContinuousActivity.RECORDING, true)
        time += 3.minutes
        assertEquals(13 * 60, assertNotNull(meter.hide()).countedSeconds)
    }

    @Test
    fun shortSegmentIsDiscarded() {
        show()
        time += 8.seconds
        assertNull(meter.hide())
        assertFalse(meter.isOpen)

        show()
        time += PracticeTimeSegment.MIN_COUNTED_SECONDS.seconds
        assertEquals(10, assertNotNull(meter.hide()).countedSeconds)
    }

    @Test
    fun snapshotClosesAtLastSaveAfterSuddenExit() {
        show()
        time += 4.minutes + 30.seconds
        val saved = assertNotNull(meter.snapshot())
        time += 29.seconds // 다음 저장 전에 앱이 종료됨

        val restored = assertNotNull(saved.close())
        assertEquals("key-1", restored.key)
        assertEquals(270, restored.visibleSeconds)
        assertEquals(270, restored.countedSeconds)
        assertEquals(WALL_START + 270_000, restored.endedAtMillis)
        assertTrue(299 - restored.visibleSeconds < PracticeTimeMeter.SnapshotInterval.inWholeSeconds)
    }

    @Test
    fun snapshotDoesNotCloseOrResetSegment() {
        show()
        time += 1.minutes
        meter.snapshot()
        time += 1.minutes
        val segment = assertNotNull(meter.hide())
        assertEquals("key-1", segment.key)
        assertEquals(120, segment.countedSeconds)
    }

    @Test
    fun showWhileOpenKeepsSegment() {
        show()
        time += 30.seconds
        show()
        time += 30.seconds
        assertEquals(60, assertNotNull(meter.hide()).countedSeconds)
        assertEquals(1, keys)
    }

    @Test
    fun secondsAreFlooredWithinPeriod() {
        show()
        time += 125.seconds + 999.milliseconds
        val segment = assertNotNull(meter.hide())
        assertEquals(125, segment.visibleSeconds)
        assertEquals(125, segment.countedSeconds)
        assertTrue(segment.visibleSeconds * 1000L <= segment.endedAtMillis - segment.startedAtMillis)
    }

    private companion object {
        const val WALL_START = 1_793_000_000_000L
    }
}

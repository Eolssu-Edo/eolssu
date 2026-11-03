package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.audio.SoundPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingAudioTest {
    @Test fun halfBeatChangesTheLitNoteAtTheHalfBeatBoundary() {
        val cells = ReadingLengthCells[1]
        assertEquals(0 to 0, readingActiveNote(cells, 0))
        assertEquals(0 to 0, readingActiveNote(cells, 499))
        assertEquals(0 to 1, readingActiveNote(cells, 500))
        assertEquals(0 to 1, readingActiveNote(cells, 999))
        assertEquals(1 to 0, readingActiveNote(cells, 1_000))
    }

    @Test fun holdAndRestHaveTheirOwnSecondBeatHighlightAndClearAtTheEnd() {
        for (index in listOf(2, 3)) {
            assertEquals(0 to 0, readingActiveNote(ReadingLengthCells[index], 999))
            assertEquals(1 to 0, readingActiveNote(ReadingLengthCells[index], 1_000))
            assertEquals(1 to 0, readingActiveNote(ReadingLengthCells[index], 1_999))
            assertNull(readingActiveNote(ReadingLengthCells[index], 2_000))
            assertNull(readingActiveNote(ReadingLengthCells[index], -1))
        }
    }

    @Test fun theVisualClockWaitsForRealPlaybackToStart() = runTest {
        val player = TestSoundPlayer().apply { gate = CompletableDeferred() }
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        session.toggle(ReadingAudioClip.Split)
        runCurrent()
        advanceTimeBy(4_000)
        assertTrue(session.state.value.loading)
        assertFalse(session.state.value.playing)
        assertEquals(0L, session.state.value.positionMs)
        player.gate!!.complete(true)
        runCurrent()
        assertTrue(session.state.value.playing)
        assertEquals(0L, session.state.value.positionMs)
        advanceTimeBy(512)
        runCurrent()
        assertEquals(0 to 1, readingActiveNote(ReadingLengthCells[1], session.state.value.positionMs))
        session.stop()
    }

    @Test fun pressingTheSameButtonStopsAndNextPressStartsFromZero() = runTest {
        val player = TestSoundPlayer()
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        session.toggle(ReadingAudioClip.Each)
        runCurrent()
        advanceTimeBy(1_200)
        session.toggle(ReadingAudioClip.Each)
        assertEquals(ReadingAudioState(), session.state.value)
        val stops = player.stops
        advanceUntilIdle()
        assertEquals(stops, player.stops)
        session.toggle(ReadingAudioClip.Each)
        runCurrent()
        assertEquals(0L, session.state.value.positionMs)
        assertEquals(listOf(ReadingAudioClip.Each.path, ReadingAudioClip.Each.path), player.paths)
        session.stop()
    }

    @Test fun switchingExamplesCancelsTheOldClockAndOnlyTheNewClipPlays() = runTest {
        val player = TestSoundPlayer()
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        session.toggle(ReadingAudioClip.Hold)
        runCurrent()
        advanceTimeBy(1_000)
        session.toggle(ReadingAudioClip.HighHwang)
        runCurrent()
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(ReadingAudioClip.HighHwang, session.state.value.clip)
        assertTrue(session.state.value.playing)
        assertFalse(session.state.value.active(ReadingAudioClip.Hold))
        advanceUntilIdle()
        assertEquals(ReadingAudioState(), session.state.value)
    }

    @Test fun stoppingWhilePreparingPreventsLateAudioAndHighlight() = runTest {
        val player = TestSoundPlayer().apply { gate = CompletableDeferred() }
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        session.toggle(ReadingAudioClip.Rest)
        runCurrent()
        session.stop()
        player.gate!!.complete(true)
        advanceUntilIdle()
        assertEquals(ReadingAudioState(), session.state.value)
        assertEquals(0, player.started)
    }

    @Test fun leavingOrBackgroundingTheScreenClearsPlaybackAndDoesNotResumeIt() = runTest {
        val player = TestSoundPlayer()
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        session.toggle(ReadingAudioClip.LowHwang)
        runCurrent()
        advanceTimeBy(200)
        session.stop()
        advanceUntilIdle()
        assertEquals(ReadingAudioState(), session.state.value)
        assertEquals(1, player.started)
    }

    @Test fun playbackFailureHasNoPretendAnimationAndCanBeRetried() = runTest {
        val player = TestSoundPlayer().apply { succeeds = false }
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        session.toggle(ReadingAudioClip.Hwang)
        advanceUntilIdle()
        assertTrue(session.state.value.failed)
        assertFalse(session.state.value.active(ReadingAudioClip.Hwang))
        assertFalse(session.state.value.playing)
        player.succeeds = true
        session.toggle(ReadingAudioClip.Hwang)
        runCurrent()
        assertTrue(session.state.value.playing)
        assertFalse(session.state.value.failed)
        session.stop()
    }

    @Test fun aPlatformExceptionIsReportedAsFailure() = runTest {
        val player = TestSoundPlayer().apply { throws = true }
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        session.toggle(ReadingAudioClip.Each)
        advanceUntilIdle()
        assertTrue(session.state.value.failed)
        assertFalse(session.state.value.playing)
    }

    @Test fun everyClipFinishesAndReleasesTheSoundAtItsOwnDuration() = runTest {
        val player = TestSoundPlayer()
        val session = ReadingAudioSession(player, this) { testScheduler.currentTime }
        for (clip in ReadingAudioClip.entries) {
            session.toggle(clip)
            runCurrent()
            advanceTimeBy(clip.durationMs - 1)
            runCurrent()
            assertTrue(session.state.value.playing)
            advanceTimeBy(20)
            runCurrent()
            assertEquals(ReadingAudioState(), session.state.value)
        }
        assertEquals(7, player.started)
    }

    private class TestSoundPlayer : SoundPlayer {
        val paths = mutableListOf<String>()
        var stops = 0
        var started = 0
        var gate: CompletableDeferred<Boolean>? = null
        var succeeds = true
        var throws = false
        override suspend fun play(path: String): Boolean {
            paths += path
            if (throws) error("Device playback failed")
            val ok = gate?.await() ?: succeeds
            if (ok) started++
            return ok
        }
        override fun stop() { stops++ }
    }
}

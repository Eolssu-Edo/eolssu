package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PracticePitchRecordingTest {
    @Test fun cancellingDispatchedStartCannotReviveRecording() = pitchTest { vm, recorder ->
        recorder.onStart = { vm.onLeaveScreen() }
        vm.grantMicAndStart()
        advanceTimeBy(3100)
        runCurrent()
        assertEquals(PracticeMode.Interrupted, vm.uiState.value.mode)
        assertTrue(recorder.cancelled)
        assertEquals(0.0, vm.uiState.value.elapsedMs)
    }

    @Test fun delayedResultKeepsOriginalEventAndClearsAtInputExpiry() = pitchTest { vm, recorder ->
        vm.grantMicAndStart()
        advanceTimeBy(3000)
        runCurrent()
        advanceTimeBy(350)
        runCurrent()
        recorder.emit(50, 100)
        runCurrent()
        assertEquals(1, vm.uiState.value.position)
        assertEquals("pitch-0", vm.uiState.value.pitchFeedback.previous?.eventId)
        assertNull(vm.uiState.value.pitchFeedback.current)
        // 입력 뒤 310ms. 100ms 음량 확인까지 기다리지 않고 안내를 지워야 한다.
        advanceTimeBy(60)
        runCurrent()
        assertNull(vm.uiState.value.pitchFeedback.previous)
    }

    @Test fun pauseResumeUsesNewSegmentAndScreenHideClearsFeedback() = pitchTest { vm, recorder ->
        vm.grantMicAndStart()
        advanceTimeBy(3100)
        runCurrent()
        recorder.emit(20, 66)
        runCurrent()
        assertEquals(PitchDirection.Match, vm.uiState.value.pitchFeedback.current?.direction)
        vm.askAbort()
        runCurrent()
        assertNull(vm.uiState.value.pitchFeedback.current)
        val cut = vm.uiState.value.elapsedMs
        vm.resumeRecording()
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(cut, vm.uiState.value.elapsedMs, 0.001)
        recorder.emit(20, 66, segment = 1)
        runCurrent()
        assertNull(vm.uiState.value.pitchFeedback.current)
        vm.onScreenHidden()
        assertEquals(PracticeMode.Interrupted, vm.uiState.value.mode)
        assertTrue(recorder.cancelled)
        assertNull(vm.uiState.value.pitchFeedback.previous)
    }

    private fun pitchTest(block: suspend TestScope.(PracticeViewModel, PitchRecorder) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val base = smallScoreSong()
            val event = base.timeline.first()
            val song = base.copy(score = base.score.copy(events = List(4) { i -> event.copy(index = i,
                id = "pitch-$i", note = ScoreNote.SourcePitch("황", 0), startTick = i.toLong(), durationTick = 1) }))
            val songs = object : SongRepository by SampleSongRepository() { override suspend fun getSong(songId: String) = song }
            val recorder = PitchRecorder { testScheduler.currentTime * 1_000_000 }
            val vm = PracticeViewModel(song.id, false, songs, PracticeSettingsStore(storage), PracticeTimeMeter({ 0L }),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder,
                playbackTimeSource = testScheduler.timeSource, recordingDispatcher = dispatcher)
            models.put("pitch", vm)
            runCurrent()
            block(vm, recorder)
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    private class PitchRecorder(private val now: () -> Long) : AudioRecorder {
        override val pitchReadings = MutableSharedFlow<PitchReading>(extraBufferCapacity = 16)
        private var boundary = CaptureBoundary(1, 0, 0, 1000, 0, true)
        override val captureBoundary get() = boundary.copy(observedNanos = now())
        override val measuresLevel = true
        var cancelled = false
        var onStart: () -> Unit = {}
        private var sequence = 0L
        override fun start() { boundary = boundary.copy(segmentId = 1, monotonicNanos = now()); cancelled = false; onStart() }
        override fun pause() {
            val samples = (now() - boundary.monotonicNanos) / 1_000_000
            boundary = boundary.copy(samplePosition = boundary.samplePosition + samples, monotonicNanos = now())
        }
        override fun resume() { boundary = boundary.copy(segmentId = boundary.segmentId + 1, monotonicNanos = now()) }
        override fun stop() = RecordedAudio("pitch.m4a", 1024)
        override fun cancel() { cancelled = true }
        override fun level() = 0.5f
        fun emit(start: Long, end: Long, segment: Long = boundary.segmentId) {
            pitchReadings.tryEmit(PitchReading(1, segment, sequence++, start, end, 1000, 311.13,
                boundary.monotonicNanos + end * 1_000_000))
        }
    }
}

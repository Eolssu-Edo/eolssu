package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.*
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
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PracticePitchConnectionTest {
    @Test fun exposesMatchingPitchWithoutChangingScreenStateAndExpiresWithoutNewInput() = exercise { vm, recorder ->
        vm.grantMicAndStart()
        advanceTimeBy(3080); runCurrent()
        val screen = vm.uiState.value
        recorder.send(20, 66, 311.13)
        runCurrent()
        assertEquals(PitchDirection.Match, vm.pitchFeedback.value.current?.direction)
        assertEquals(screen.song!!.timeline[0].id, vm.pitchFeedback.value.current?.eventId)
        assertEquals(screen, vm.uiState.value)
        advanceTimeBy(170); runCurrent()
        assertEquals(screen.song.timeline[0].id, vm.pitchFeedback.value.previous?.eventId)
        advanceTimeBy(117); runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
    }

    @Test fun pauseResumeClearsFeedbackAndAnchorsNewSamplesAtPausedScoreTime() = exercise { vm, recorder ->
        vm.grantMicAndStart(); advanceTimeBy(3080); runCurrent()
        recorder.send(20, 66, 311.13); runCurrent()
        assertNotNull(vm.pitchFeedback.value.current)
        vm.askAbort(); runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
        recorder.send(20, 66, 311.13); runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
        vm.resumeRecording(); advanceTimeBy(3080); runCurrent()
        recorder.send(20, 66, 311.13); runCurrent()
        assertEquals(PitchDirection.Match, vm.pitchFeedback.value.current?.direction)
        assertEquals(vm.uiState.value.song!!.timeline[0].id, vm.pitchFeedback.value.current?.eventId)
        vm.onLeaveScreen(); runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
    }

    @Test fun unconfirmedSilenceUnavailableAndFinishedRecordingCannotLeaveMatchingFeedback() = exercise { vm, recorder ->
        vm.grantMicAndStart(); advanceTimeBy(3080); runCurrent()
        recorder.send(20, 66, 311.13); runCurrent()
        assertEquals(PitchDirection.Match, vm.pitchFeedback.value.current?.direction)
        advanceTimeBy(20)
        recorder.send(40, 86, null); runCurrent()
        assertNull(vm.pitchFeedback.value.current?.direction)
        recorder.send(40, 86, 311.13, available = false); runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
        advanceTimeBy(4000); runCurrent()
        assertTrue(vm.uiState.value.finished)
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
    }

    @Test fun exposesLowAndHighAndIgnoresReadingsFromPreviousRecording() = exercise { vm, recorder ->
        vm.grantMicAndStart(); advanceTimeBy(3080); runCurrent()
        // 황(311Hz)이 곡의 최저음이라 그보다 200센트 넘게 낮은 값은 보류된다(S15P31E102-556 D-5). 290Hz는 122센트 낮음.
        recorder.send(20, 66, 290.0); runCurrent()
        assertEquals(PitchDirection.Low, vm.pitchFeedback.value.current?.direction)
        advanceTimeBy(20)
        recorder.send(40, 86, 400.0); runCurrent()
        assertEquals(PitchDirection.High, vm.pitchFeedback.value.current?.direction)
        vm.restartNow(); advanceTimeBy(3080); runCurrent()
        recorder.pitchReadings.emit(PitchReading(1, 1, 99, 20, 66, 1000, 311.13, recorder.now() - 10_000_000))
        runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
        recorder.send(20, 66, 311.13); runCurrent()
        assertEquals(PitchDirection.Match, vm.pitchFeedback.value.current?.direction)
    }

    @Test fun waitsForValidInputTimestampWithoutMovingScoreAnchor() = exercise { vm, recorder ->
        recorder.validTimestamp = false
        vm.grantMicAndStart(); advanceTimeBy(3080); runCurrent()
        recorder.send(20, 66, 311.13); runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
        recorder.validTimestamp = true
        advanceTimeBy(20)
        recorder.send(40, 86, 311.13); runCurrent()
        assertEquals(vm.uiState.value.song!!.timeline[0].id, vm.pitchFeedback.value.current?.eventId)
        assertEquals(PitchDirection.Match, vm.pitchFeedback.value.current?.direction)
    }

    @Test fun accountChangeClearsResultsImmediatelyAndCannotReviveOldCollector() = exercise { vm, recorder ->
        vm.grantMicAndStart(); advanceTimeBy(3080); runCurrent()
        recorder.send(20, 66, 311.13); runCurrent()
        assertNotNull(vm.pitchFeedback.value.current)
        recorder.member.changing { recorder.member.set("next-member") }
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
        recorder.send(20, 66, 311.13); runCurrent()
        assertEquals(PracticePitchFeedback(), vm.pitchFeedback.value)
    }

    private fun exercise(block: suspend TestScope.(PracticeViewModel, PitchRecorder) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage)
            val song = smallScoreSong()
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String) = song
            }
            val member = SignedInMember(storage)
            member.set("first-member")
            val recorder = PitchRecorder(member) { testScheduler.currentTime * 1_000_000 }
            val vm = PracticeViewModel(song.id, false, songs, settings, PracticeTimeMeter({ 0L }),
                PracticeTimeQueue(storage, null, member, backgroundScope), recorder,
                playbackTimeSource = testScheduler.timeSource, member = member)
            models.put("practice", vm)
            runCurrent()
            block(vm, recorder)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }

    private class PitchRecorder(val member: SignedInMember, val now: () -> Long) : AudioRecorder {
        override val pitchReadings = MutableSharedFlow<PitchReading>(extraBufferCapacity = 8)
        var validTimestamp = true
        private var recording = 0L
        private var segment = 0L
        private var sequence = 0L
        private var start = 0L
        private var segmentNanos = 0L
        override val captureBoundary get() = CaptureBoundary(recording, segment, start, 1000, segmentNanos, validTimestamp, now())
        override val measuresLevel = false
        override fun start() { recording++; segment = 1; start = 0; sequence = 0; segmentNanos = now() }
        override fun pause() = Unit
        override fun resume() { segment++; start = 80; segmentNanos = now() }
        override fun stop() = RecordedAudio("take.m4a", 1024)
        override fun cancel() = Unit
        override fun level() = 0f
        suspend fun send(from: Long, to: Long, hz: Double?, available: Boolean = true) {
            pitchReadings.emit(PitchReading(recording, segment, sequence++, start + from, start + to, 1000, hz,
                segmentNanos + to * 1_000_000, analysisAvailable = available))
        }
    }
}

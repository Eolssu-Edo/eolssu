package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.audio.RecordingSpec
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingSupportTest {
    @Test fun publicRecordingCarriesTheScoreVersionUsedAtItsStart() =
        recordingTest(FakePracticeRecorder(), song = smallScoreSong().copy(scoreVersion = 1)) { vm, _ ->
            vm.grantMicAndStart()
            finishSong(vm)
            assertEquals(1, vm.takeForEvaluation()?.scoreVersion)
        }

    @Test fun noRecorderCannotStartEvaluationEvenAfterPermissionWasRemembered() = recordingTest { vm, settings ->
        settings.grantMic()
        vm.requestEvaluation()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertNull(vm.uiState.value.popup)
        advanceTimeBy(4000)
        runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertEquals(emptySet(), vm.uiState.value.runningActivities)
        assertFalse(vm.uiState.value.recordingSupported)
        assertNull(vm.takeForEvaluation())
    }

    @Test fun unsupportedCallbacksCannotGrantPermissionOrStartARecording() = recordingTest { vm, settings ->
        vm.requestEvaluation()
        assertNull(vm.uiState.value.popup) // 미지원은 권한 거부와 다르다.
        assertFalse(vm.requestMicPermission())
        vm.grantMicAndStart()
        vm.restartNow()
        vm.resumeRecording()
        advanceTimeBy(4000)
        runCurrent()
        assertFalse(settings.micGranted.value)
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.finished)
        assertNull(vm.takeForEvaluation())
        vm.openPanel(PracticePanel.Settings)
        assertEquals(PracticePanel.Settings, vm.uiState.value.panel)
    }

    @Test fun soloPracticeAlsoReportsUnsupportedRecording() = recordingTest(solo = true) { vm, _ ->
        assertTrue(vm.uiState.value.solo)
        assertFalse(vm.uiState.value.recordingSupported)
        vm.requestEvaluation()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
    }

    @Test fun deniedPermissionDoesNotStartTheSupportedRecorder() {
        val recorder = FakePracticeRecorder()
        val denied = object : MicPermission {
            override fun isGranted() = false
            override suspend fun request() = false
        }
        recordingTest(recorder, denied) { vm, settings ->
            settings.grantMic()
            vm.requestEvaluation()
            assertTrue(vm.uiState.value.recordingSupported)
            runCurrent()
            assertNull(vm.uiState.value.popup) // 서비스 안내 창 없이 기기 권한만 묻고, 다시 물을 수 있는 거부는 안내 창을 띄우지 않는다.
            assertFalse(vm.requestMicPermission())
            advanceTimeBy(4000)
            runCurrent()
            assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
            assertEquals(0, recorder.starts)
        }
    }

    @Test fun supportedRecorderWithoutLevelMeasurementProducesARealTake() = recordingTest(FakePracticeRecorder()) { vm, _ ->
        assertTrue(vm.uiState.value.recordingSupported)
        assertFalse(vm.uiState.value.inputMeasured)
        assertTrue(vm.requestMicPermission())
        vm.grantMicAndStart()
        assertNull(vm.takeForEvaluation())
        finishSong(vm)
        assertEquals(PracticePopup.End, vm.uiState.value.popup)
        assertEquals(RecordedTake("take.m4a", "take-key"), vm.takeForEvaluation())
        vm.toReady()
        assertNull(vm.takeForEvaluation())
    }

    @Test fun microphoneIsPreparedOnceDuringTheCountdownBeforeRecordingStarts() {
        val recorder = FakePracticeRecorder()
        recordingTest(recorder) { vm, _ ->
            vm.grantMicAndStart()
            runCurrent()
            assertEquals(1, recorder.prepares)
            assertEquals(0, recorder.starts)
            advanceTimeBy(3100)
            runCurrent()
            assertEquals(1, recorder.starts)
            assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        }
    }

    @Test fun microphoneThatCannotBeOpenedFailsTheTakeWithoutStartingIt() {
        val recorder = FakePracticeRecorder(prepareFailure = IllegalStateException("NotAllowedError"))
        recordingTest(recorder) { vm, _ ->
            vm.grantMicAndStart()
            advanceTimeBy(3100)
            runCurrent()
            assertEquals(0, recorder.starts)
            assertEquals(PracticePopup.RecordFailed, vm.uiState.value.popup)
            assertNull(vm.takeForEvaluation())
        }
    }

    @Test fun cancellingTheCountdownClosesThePreparedMicrophone() {
        val recorder = FakePracticeRecorder()
        recordingTest(recorder) { vm, _ ->
            vm.grantMicAndStart()
            runCurrent()
            vm.cancelCountdown()
            advanceTimeBy(4000)
            runCurrent()
            assertEquals(1, recorder.cancels)
            assertEquals(0, recorder.starts)
            assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        }
    }

    @Test fun failedSaveCannotBeSubmittedForAnalysis() = recordingTest(FakePracticeRecorder(output = null)) { vm, _ ->
        vm.grantMicAndStart()
        finishSong(vm)
        assertEquals(PracticePopup.RecordFailed, vm.uiState.value.popup)
        assertFalse(vm.uiState.value.finished)
        assertNull(vm.takeForEvaluation())
    }

    @Test fun recordingLimitShowsItsOwnNoticeAndKeepsTheTakeForEvaluation(): TestResult {
        val song = smallScoreSong()
        val slowSong = song.copy(baseBpm = 1, score = song.score.copy(
            timing = requireNotNull(song.score.timing).copy(ticksPerBeat = 1),
        ))
        // 가상 시간 10분을 흘리면 진행·입력 크기 갱신이 수만 번 돌아 브라우저 테스트 제한(2초)을 넘는다(S15P31E102-737).
        // 한도 동작은 짧은 한도로 확인하고, 실제 한도가 10분인지는 따로 본다.
        assertEquals(10.minutes, RecordingSpec.MaxDuration)
        return recordingTest(FakePracticeRecorder(), song = slowSong, maxRecording = 5.seconds) { vm, _ ->
            vm.grantMicAndStart()
            advanceTimeBy(3_000 + 4_999)
            runCurrent()
            assertFalse(vm.uiState.value.finished)
            assertNull(vm.takeForEvaluation())

            advanceTimeBy(1)
            runCurrent()
            assertTrue(vm.uiState.value.finished)
            assertEquals(PracticePopup.EndAtLimit, vm.uiState.value.popup)
            assertEquals(RecordedTake("take.m4a", "take-key"), vm.takeForEvaluation())
        }
    }

    @Test fun idleScoreDoesNotShowANotice() = recordingTest(FakePracticeRecorder()) { vm, _ ->
        vm.onScreenShown()
        runCurrent()
        val ready = vm.uiState.value

        advanceTimeBy(570_000)
        runCurrent()
        assertEquals(ready, vm.uiState.value)

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(ready, vm.uiState.value)
    }

    @Test fun oversizedRecordingCannotBeSubmittedForAnalysis() =
        recordingTest(FakePracticeRecorder(output = RecordedAudio("large.m4a", RecordingSpec.MAX_BYTES + 1))) { vm, _ ->
            vm.grantMicAndStart()
            finishSong(vm)
            assertEquals(PracticePopup.TooLarge, vm.uiState.value.popup)
            assertNull(vm.takeForEvaluation())
        }

    @Test fun interruptedRecordingCannotBeSubmittedAndCanRestart() {
        val recorder = FakePracticeRecorder()
        recordingTest(recorder) { vm, _ ->
            vm.grantMicAndStart()
            advanceTimeBy(3000)
            runCurrent()
            assertEquals(1, recorder.starts)
            vm.onLeaveScreen()
            assertEquals(PracticeMode.Interrupted, vm.uiState.value.mode)
            assertNull(vm.takeForEvaluation())
            vm.restartNow()
            finishSong(vm)
            assertEquals(2, recorder.starts)
            assertEquals(RecordedTake("take.m4a", "take-key"), vm.takeForEvaluation())
        }
    }

    @Test fun systemInterruptionStopsTheTakeLikeLeavingTheScreen() {
        val recorder = FakePracticeRecorder()
        recordingTest(recorder) { vm, _ ->
            vm.grantMicAndStart()
            advanceTimeBy(4000)
            runCurrent()
            assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
            recorder.interrupted = true
            advanceTimeBy(200)
            runCurrent()
            assertEquals(PracticeMode.Interrupted, vm.uiState.value.mode)
            assertEquals(1, recorder.cancels)
            assertNull(vm.takeForEvaluation())
            vm.restartNow()
            finishSong(vm)
            assertEquals(2, recorder.starts)
            assertEquals(RecordedTake("take.m4a", "take-key"), vm.takeForEvaluation())
        }
    }

    private fun TestScope.finishSong(vm: PracticeViewModel) {
        advanceTimeBy(3000 + requireNotNull(vm.uiState.value.song).durationMs().toLong() + 100)
        runCurrent()
    }

    private fun recordingTest(
        recorder: AudioRecorder? = null,
        permission: MicPermission? = null,
        solo: Boolean = false,
        song: Song = smallScoreSong(),
        maxRecording: Duration = RecordingSpec.MaxDuration,
        block: suspend TestScope.(PracticeViewModel, PracticeSettingsStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage)
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String) = song
            }
            val vm = PracticeViewModel(song.id, solo, songs, settings, PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder, permission,
                playbackTimeSource = testScheduler.timeSource, newKey = { "take-key" }, maxRecording = maxRecording)
            models.put("practice", vm)
            runCurrent()
            block(vm, settings)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}

/** 파일 저장과 마이크만 대체한다. 화면 상태와 시간 진행은 실제 ViewModel을 사용한다. */
internal class FakePracticeRecorder(
    private val output: RecordedAudio? = RecordedAudio("take.m4a", 1024),
    /** 웹처럼 마이크를 여는 [prepare]가 실패하는 경우. */
    private val prepareFailure: Exception? = null,
) : AudioRecorder {
    var starts = 0
        private set
    var cancels = 0
        private set
    var prepares = 0
        private set
    override var interrupted = false
    override val measuresLevel = false
    override suspend fun prepare() {
        prepares++
        prepareFailure?.let { throw it }
    }
    override fun start() { starts++; interrupted = false }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = output
    override fun cancel() { cancels++ }
    override fun level() = 0f
}

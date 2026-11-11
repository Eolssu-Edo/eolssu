package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.*
import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeEchoCalibrationTest {
    @Test fun preparationJangdanCountsThreeTwoOneAndStartsRecordingWithoutAnotherTimer() = practice { vm, recorder, player, _, _ ->
        vm.requestEvaluation(); runCurrent()
        assertEquals(1, recorder.preparations)
        assertSame(player, recorder.player)
        assertTrue(recorder.clips.any { it.targetFrames != null }, "Stretched rolls must be calibrated")
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, recorder.starts)
        assertEquals(3, vm.uiState.value.count)
        assertTrue(vm.uiState.value.echoMeasuring)
        assertNotNull(vm.uiState.value.echoProgress)
        recorder.reportCount(2); runCurrent()
        assertEquals(2, vm.uiState.value.count)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, vm.uiState.value.count, "Elapsed seconds do not advance preparation jangdan")
        recorder.reportCount(1); runCurrent()
        assertEquals(1, vm.uiState.value.count)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, vm.uiState.value.count)
        assertEquals(0, recorder.starts, "The last group must finish collection and validation before recording")
        recorder.pending.complete(true); runCurrent()
        assertEquals(1, recorder.starts)
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.echoMeasuring)
        assertFalse(vm.uiState.value.echoCorrectionOff)
    }

    @Test fun failedCalibrationNeverStartsAFileAndCanRetry() = practice { vm, recorder, _, _, _ ->
        vm.requestEvaluation(); runCurrent()
        recorder.reportCount(1); runCurrent()
        recorder.pending.complete(false); runCurrent()
        assertEquals(PracticePopup.EchoCalibrationFailed, vm.uiState.value.popup)
        assertFalse(vm.uiState.value.echoMeasuring)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, recorder.starts)
        assertEquals(emptySet(), vm.uiState.value.runningActivities)
        recorder.pending = CompletableDeferred()
        vm.retryEchoCalibration(); runCurrent()
        assertEquals(2, recorder.preparations)
        assertNull(vm.uiState.value.popup)
        assertTrue(vm.uiState.value.echoMeasuring)
        assertEquals(3, vm.uiState.value.count, "Retry starts a new three-group count-in")
        recorder.pending.complete(true); runCurrent()
        assertEquals(1, recorder.starts)
    }

    @Test fun explicitSkipDiscardsCalibrationAndOnlySkipsOneTake() = practice { vm, recorder, _, _, _ ->
        vm.requestEvaluation(); runCurrent()
        recorder.pending.complete(false); runCurrent()
        vm.continueWithoutEcho(); runCurrent()
        assertEquals(1, recorder.discards)
        assertEquals(3, vm.uiState.value.count)
        advanceTimeBy(2_999); runCurrent()
        assertEquals(0, recorder.starts)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, recorder.preparations)
        assertEquals(1, recorder.starts)
        assertTrue(vm.uiState.value.echoCorrectionOff)
        recorder.pending = CompletableDeferred()
        vm.restartNow(); runCurrent()
        assertEquals(2, recorder.preparations)
        assertTrue(vm.uiState.value.echoMeasuring)
        vm.cancelCountdown()
    }

    @Test fun cancelledLatePreparationCannotStartOrRestoreUi() = practice { vm, recorder, player, _, _ ->
        recorder.ignoreCancellation = true
        vm.requestEvaluation(); runCurrent()
        val stops = player.stops
        vm.cancelCountdown(); runCurrent()
        assertEquals(1, recorder.cancels)
        assertTrue(player.stops > stops)
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        val countAfterCancel = vm.uiState.value.count
        recorder.reportCount(1); runCurrent()
        assertEquals(countAfterCancel, vm.uiState.value.count, "A cancelled producer cannot restore the count")
        recorder.pending.complete(true); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        assertEquals(0, recorder.starts)
        assertFalse(vm.uiState.value.echoMeasuring)
        assertNull(vm.uiState.value.echoProgress)
        assertNull(vm.uiState.value.popup)
        assertEquals(RecordingEchoStatus.Off, vm.uiState.value.echoStatus)
    }

    @Test fun hiddenScreenCancelsCalibrationAndLateCallbacks() = practice { vm, recorder, player, _, _ ->
        recorder.ignoreCancellation = true
        vm.requestEvaluation(); runCurrent()
        val stops = player.stops
        vm.onScreenHidden(); runCurrent()
        assertEquals(1, recorder.cancels)
        assertTrue(player.stops > stops)
        assertEquals(PracticeMode.Interrupted, vm.uiState.value.mode)
        recorder.pending.complete(true); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        assertEquals(0, recorder.starts)
        assertFalse(vm.uiState.value.echoMeasuring)
    }

    @Test fun newPreparationWaitsForCancelledPreparationToReleaseSharedPlayer() = practice { vm, recorder, _, _, _ ->
        recorder.ignoreCancellation = true
        vm.requestEvaluation(); runCurrent()
        val old = recorder.pending
        vm.cancelCountdown(); runCurrent()
        recorder.pending = CompletableDeferred()
        vm.requestEvaluation(); runCurrent()
        val beforeOldFinished = recorder.preparations
        old.complete(true); runCurrent()
        val waiting = vm.uiState.value
        val startsBeforePrepared = recorder.starts
        recorder.pending.complete(true); runCurrent()
        assertEquals(1, beforeOldFinished, "Old cleanup must finish before the shared player is reused")
        assertEquals(2, recorder.preparations)
        assertTrue(waiting.echoMeasuring)
        assertEquals(3, waiting.count)
        assertEquals(0, startsBeforePrepared)
        assertEquals(1, recorder.starts)
    }

    @Test fun repeatedCancelAndRestartCannotSkipOutstandingPlayerCleanup() = practice { vm, recorder, _, _, _ ->
        recorder.ignoreCancellation = true
        vm.requestEvaluation(); runCurrent()
        val old = recorder.pending
        repeat(2) {
            vm.cancelCountdown(); runCurrent()
            recorder.pending = CompletableDeferred()
            vm.requestEvaluation(); runCurrent()
        }
        val beforeOldFinished = recorder.preparations
        old.complete(true); runCurrent()
        recorder.pending.complete(true); runCurrent()
        assertEquals(1, beforeOldFinished)
        assertEquals(2, recorder.preparations)
        assertEquals(1, recorder.starts)
    }

    @Test fun backReleasesCalibrationThenKeepPlayingMeasuresAgain() = practice { vm, recorder, player, _, _ ->
        vm.requestEvaluation(); runCurrent()
        val stops = player.stops
        assertFalse(vm.onBack()); runCurrent()
        assertEquals(1, recorder.cancels)
        assertTrue(player.stops > stops)
        assertEquals(PracticePopup.Exit, vm.uiState.value.popup)
        recorder.pending = CompletableDeferred()
        vm.keepPlayingAfterExit(); runCurrent()
        assertEquals(2, recorder.preparations)
        assertTrue(vm.uiState.value.echoMeasuring)
        vm.cancelCountdown()
    }

    @Test fun accountChangeClearsPendingCalibration() = practice { vm, recorder, _, _, member ->
        recorder.ignoreCancellation = true
        vm.requestEvaluation(); runCurrent()
        member.set("other-account"); runCurrent()
        assertTrue(recorder.cancels > 0)
        assertNull(vm.uiState.value.song)
        recorder.pending.complete(true); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        assertEquals(0, recorder.starts)
        assertFalse(vm.uiState.value.echoMeasuring)
        assertNull(vm.uiState.value.popup)
    }

    @Test fun resumedTakeDoesNotCalibrateAndCancellingResumeKeepsPausedTake() = practice { vm, recorder, _, _, _ ->
        vm.requestEvaluation(); runCurrent()
        recorder.pending.complete(true); runCurrent()
        vm.askAbort(); runCurrent()
        vm.resumeRecording(); runCurrent()
        assertFalse(vm.uiState.value.echoMeasuring)
        val cancels = recorder.cancels
        vm.cancelCountdown(); runCurrent()
        assertEquals(cancels, recorder.cancels)
        assertEquals(PracticePopup.Abort, vm.uiState.value.popup)
        vm.resumeRecording(); runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(1, recorder.preparations)
        assertEquals(1, recorder.starts)
        assertEquals(1, recorder.resumes)
        assertTrue(vm.uiState.value.echoCorrectionOff)
    }

    @Test fun changedOrUnreliableStatusShowsCorrectionOffWithoutStoppingRecording() = practice { vm, recorder, _, _, _ ->
        vm.requestEvaluation(); runCurrent()
        recorder.pending.complete(true); runCurrent()
        for (status in listOf(RecordingEchoStatus.Changed, RecordingEchoStatus.Unreliable, RecordingEchoStatus.Unavailable)) {
            recorder.echoStatus.value = status; runCurrent()
            assertTrue(vm.uiState.value.echoCorrectionOff)
            assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
            assertNull(vm.uiState.value.popup)
        }
        assertEquals(0, recorder.cancels)
    }

    @Test fun jangdanOffStartsNormallyAndTurningItOnDoesNotCalibrateMidTake() = practice { vm, recorder, _, settings, _ ->
        settings.update { it.copy(jangdanSound = false) }; runCurrent()
        vm.requestEvaluation(); runCurrent()
        assertFalse(vm.uiState.value.echoMeasuring)
        advanceTimeBy(2_999); runCurrent()
        assertEquals(0, recorder.starts)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, recorder.starts)
        assertEquals(0, recorder.preparations)
        assertFalse(vm.uiState.value.echoCorrectionOff)
        settings.update { it.copy(jangdanSound = true) }; runCurrent()
        assertTrue(vm.uiState.value.echoCorrectionOff)
        assertEquals(0, recorder.preparations)
        settings.update { it.copy(jangdanSound = false) }; runCurrent()
        assertFalse(vm.uiState.value.echoCorrectionOff)
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
    }

    @Test fun unsupportedRecorderKeepsTheThreeSecondCountdown() = practice { vm, recorder, _, _, _ ->
        recorder.supportsEchoCalibration = false
        vm.requestEvaluation(); runCurrent()
        assertEquals(3, vm.uiState.value.count)
        assertFalse(vm.uiState.value.echoMeasuring)
        advanceTimeBy(2_999); runCurrent()
        assertEquals(0, recorder.starts)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, recorder.starts)
        assertEquals(0, recorder.preparations)
    }

    private fun practice(
        block: suspend TestScope.(PracticeViewModel, EchoRecorder, EchoPlayer, PracticeSettingsStore, SignedInMember) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val views = ViewModelStore()
        try {
            val song = smallScoreSong().copy(jangdanPlayback = JangdanPlayback("2", "굿거리", 0, 4, listOf("더러러러", "쿵"), listOf(1, 1)))
            val repository = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String): Song = song
            }
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage).apply {
                grantMic()
                update { it.copy(jangdanSound = true) }
            }
            val recorder = EchoRecorder()
            val player = EchoPlayer()
            val member = SignedInMember(storage)
            val vm = PracticeViewModel(song.id, false, repository, settings, PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, member, backgroundScope), recorder,
                playbackTimeSource = testScheduler.timeSource, jangdanPlayer = player, member = member)
            views.put("practice", vm)
            runCurrent()
            block(vm, recorder, player, settings, member)
        } finally { views.clear(); Dispatchers.resetMain() }
    }
}

private class EchoRecorder : AudioRecorder {
    override val measuresLevel = false
    override var supportsEchoCalibration = true
    override val echoStatus = MutableStateFlow(RecordingEchoStatus.Off)
    var preparations = 0
    var starts = 0
    var cancels = 0
    var resumes = 0
    var discards = 0
    var pending = CompletableDeferred<Boolean>()
    var ignoreCancellation = false
    var player: JangdanSamplePlayer? = null
    var clips = emptySet<JangdanClip>()
    private var publishProgress: (EchoCalibrationProgress) -> Unit = {}
    fun reportCount(count: Int) = publishProgress(EchoCalibrationProgress(0, clips.size * 3, count))
    override suspend fun prepareEcho(player: JangdanSamplePlayer, plan: JangdanPlan, progress: (EchoCalibrationProgress) -> Unit): Boolean {
        val clips = plan.strikes.map { it.clip }.toSet()
        preparations++
        this.player = player
        this.clips = clips
        publishProgress = progress
        echoStatus.value = RecordingEchoStatus.Measuring
        progress(EchoCalibrationProgress(0, clips.size * 3, 3))
        val prepared = if (ignoreCancellation) suspendCoroutine { continuation ->
            pending.invokeOnCompletion { continuation.resume(true) }
        } else pending.await()
        progress(EchoCalibrationProgress(clips.size * 3, clips.size * 3, 1))
        return prepared.also { echoStatus.value = if (it) RecordingEchoStatus.Ready else RecordingEchoStatus.NotQuiet }
    }
    override fun discardEchoCalibration() { discards++; echoStatus.value = RecordingEchoStatus.Off }
    override fun start() { starts++; echoStatus.value = if (echoStatus.value == RecordingEchoStatus.Ready) RecordingEchoStatus.Active else RecordingEchoStatus.Off }
    override fun pause() { echoStatus.value = RecordingEchoStatus.Off }
    override fun resume() { resumes++ }
    override fun stop() = RecordedAudio("echo.m4a", 1024)
    override fun cancel() { cancels++; echoStatus.value = RecordingEchoStatus.Off }
    override fun level() = 0f
}

private class EchoPlayer : JangdanSamplePlayer {
    override val available = true
    override val interrupted = false
    var stops = 0
    override suspend fun prepare(clips: Set<JangdanClip>) = true
    override fun begin(accompanyingModel: Boolean) = true
    override fun play(clip: JangdanClip, gain: Float) = true
    override fun silence() = Unit
    override fun stop() { stops++ }
    override fun release() = stop()
}

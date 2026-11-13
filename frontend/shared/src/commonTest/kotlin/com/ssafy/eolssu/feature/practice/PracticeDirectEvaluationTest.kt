package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.MicPermission
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 곡 정보의 '연주 평가 시작'으로 들어온 연주 평가(S15P31E102-705): 들어오면 평가 대기 상태로 두고 '평가 시작'을 눌러야 시작한다(S15P31E102-747).
 * 녹음을 분석에 넘긴 뒤 돌아오면 대기 상태를 다시 보이지 않고 들어온 화면으로 돌아갈 상태가 된다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeDirectEvaluationTest {
    @Test fun cachedOldWholeScoreCannotStartCountdownOrMicrophone() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage).apply { grantMic() }
            val old = smallScoreSong().copy(id = "42", scoreVersion = 1)
            var current = old
            var currentLookups = 0
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun retainedSong(songId: String, scoreVersion: Int?) = old
                override suspend fun getSong(songId: String) = current.also { currentLookups++ }
            }
            val recorder = FakePracticeRecorder()
            val vm = PracticeViewModel(old.id, false, songs, settings, PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder,
                playbackTimeSource = testScheduler.timeSource, direct = true)
            models.put("stale", vm)
            runCurrent()
            assertTrue(vm.uiState.value.awaitingStart)
            assertEquals(old, vm.uiState.value.song)
            assertEquals(0, currentLookups)
            assertEquals(0, recorder.starts)
            current = old.copy(scoreVersion = 2)
            vm.requestEvaluation()
            runCurrent()
            assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
            assertEquals(PracticePopup.ScoreChanged, vm.uiState.value.popup)
            assertEquals(1, currentLookups)
            assertFalse(vm.leavesDirectOnReturn())
            assertEquals(0, recorder.starts)
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test fun publicationWhilePermissionIsOpenDoesNotOpenTheMicrophone() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage)
            val old = smallScoreSong().copy(id = "42", scoreVersion = 1)
            var current = old
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String) = current
            }
            val recorder = FakePracticeRecorder()
            val answer = CompletableDeferred<Boolean>()
            val permission = object : MicPermission {
                override fun isGranted() = false
                override suspend fun request() = answer.await()
            }
            val vm = PracticeViewModel(old.id, false, songs, settings, PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder, permission,
                playbackTimeSource = testScheduler.timeSource, direct = true)
            models.put("published", vm)
            runCurrent()
            vm.requestEvaluation()
            runCurrent()
            assertNull(vm.uiState.value.popup)
            assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
            current = old.copy(scoreVersion = 2)
            answer.complete(true)
            runCurrent()
            assertEquals(0, recorder.starts)
            assertEquals(PracticePopup.ScoreChanged, vm.uiState.value.popup)
            vm.micDenied() // 권한 처리 콜백이 판본 변경 안내를 덮지 않는다.
            assertEquals(PracticePopup.ScoreChanged, vm.uiState.value.popup)
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test fun directEvaluationWaitsForStartButtonOnEntry() = directTest { vm, settings ->
        settings.grantMic()
        assertTrue(vm.uiState.value.direct)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.awaitingStart)
        assertTrue(vm.uiState.value.recordingLayout)
        assertFalse(vm.leavesDirectOnReturn())
    }

    /** 기기 권한만 보고 서비스 마이크 안내 창은 띄우지 않는다(S15P31E102-733). 테스트의 기기 권한은 없음(null) = 허용이다. */
    @Test fun startButtonBeginsCountdownWithoutServiceMicNotice() = directTest { vm, _ ->
        vm.requestEvaluation()
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.awaitingStart)
        assertNull(vm.uiState.value.popup)
        assertFalse(vm.leavesDirectOnReturn())
    }

    @Test fun cancelledCountdownReturnsToWaitingStateAndStaysOnScreen() = directTest { vm, _ ->
        vm.requestEvaluation()
        vm.cancelCountdown()
        runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.awaitingStart)
        assertNull(vm.uiState.value.popup)
        assertFalse(vm.leavesDirectOnReturn()) // 기기 권한 창이 닫히며 화면이 다시 보여도 대기 상태에 남는다.
    }

    /** 연주 중단 창의 '연주 대기로'(S15P31E102-750): 화면을 나가지 않고 녹음을 버린 뒤 평가 대기 상태로 돌아온다. */
    @Test fun abortingPerformanceReturnsToWaitingStateOnScreen() = FakePracticeRecorder().let { abortRecorder -> directTest(fake = abortRecorder) { vm, _ ->
        vm.requestEvaluation()
        advanceTimeBy(3_000 + 1_000)
        runCurrent()
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.elapsedMs > 0.0)
        vm.askAbort()
        assertEquals(PracticePopup.Abort, vm.uiState.value.popup)
        val cancelsBefore = abortRecorder.cancels
        vm.toReady()
        runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.awaitingStart)
        assertNull(vm.uiState.value.popup)
        assertEquals(0.0, vm.uiState.value.elapsedMs)
        assertTrue(abortRecorder.cancels > cancelsBefore) // 멈춰 둔 녹음을 버린다.
        assertNull(vm.takeForEvaluation())
        assertFalse(vm.leavesDirectOnReturn()) // 분석으로 넘긴 것이 아니므로 화면에 머문다.
        // 대기 상태에서 다시 '평가 시작'을 누르면 처음부터 새로 센다.
        vm.requestEvaluation()
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
        assertEquals(3, vm.uiState.value.count)
    } }

    @Test fun returnAfterHandingTakeToAnalysisLeavesWaitingState() = directTest { vm, _ ->
        vm.requestEvaluation()
        finishSong(vm)
        assertEquals(PracticePopup.End, vm.uiState.value.popup)
        assertNotNull(vm.takeForEvaluation())
        vm.toReady() // 분석으로 넘기기 전 준비 상태로 돌린 것과 같다.
        assertTrue(vm.leavesDirectOnReturn())
    }

    @Test fun switchedEvaluationKeepsReadyScreen() = directTest(direct = false) { vm, _ ->
        assertFalse(vm.uiState.value.direct)
        vm.requestEvaluation()
        finishSong(vm)
        assertNotNull(vm.takeForEvaluation())
        vm.toReady()
        assertFalse(vm.leavesDirectOnReturn())
    }

    @Test fun soloPracticeIgnoresDirectFlag() = directTest(solo = true) { vm, _ ->
        assertFalse(vm.uiState.value.direct)
    }

    @Test fun noRecorderKeepsReadyScreenWithUnsupportedNotice() = directTest(recorder = false) { vm, _ ->
        assertFalse(vm.uiState.value.direct)
        assertFalse(vm.uiState.value.recordingSupported)
    }

    /** 연주 평가 제목 옆 '쉬운 표기' 스위치는 한글 읽는 법 표시를 바로 켜고 끈다(S15P31E102-716). */
    @Test fun titleNotationSwitchTogglesHangulImmediately() = directTest(direct = false) { vm, settings ->
        vm.toggleHangul()
        runCurrent()
        assertTrue(settings.settings.value.showHangul)
        assertTrue(vm.uiState.value.settings.showHangul)
        vm.toggleHangul()
        runCurrent()
        assertFalse(settings.settings.value.showHangul)
        assertFalse(vm.uiState.value.settings.showHangul)
    }

    private fun TestScope.finishSong(vm: PracticeViewModel) {
        advanceTimeBy(3000 + requireNotNull(vm.uiState.value.song).durationMs().toLong() + 100)
        runCurrent()
    }

    private fun directTest(
        direct: Boolean = true,
        solo: Boolean = false,
        recorder: Boolean = true,
        fake: FakePracticeRecorder = FakePracticeRecorder(),
        block: suspend TestScope.(PracticeViewModel, PracticeSettingsStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage)
            val song = smallScoreSong()
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String) = song
            }
            val vm = PracticeViewModel(song.id, solo, songs, settings, PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), if (recorder) fake else null,
                playbackTimeSource = testScheduler.timeSource, newKey = { "take-key" }, direct = direct)
            models.put("practice", vm)
            runCurrent()
            block(vm, settings)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}

package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.MicPermission
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * '연주 평가 시작'의 마이크 권한(S15P31E102-733): 서비스 안내 창 없이 기기 권한 창만 띄우고,
 * 허용될 때만 카운트다운을 시작한다. 차단돼 기기가 더 묻지 않을 때만 설정 안내를 띄운다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeEvaluationMicPermissionTest {
    @Test fun alreadyGrantedStartsCountdownWithoutAskingEvenIfAppFlagIsMissing() = micTest(Mic(granted = true)) { vm, mic, settings, recorder ->
        vm.requestEvaluation()
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
        assertNull(vm.uiState.value.popup)
        assertEquals(0, mic.requests)
        assertTrue(settings.micGranted.value)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        assertEquals(1, recorder.starts)
    }

    @Test fun notYetAskedShowsOnlyDevicePromptAndStartsAfterAllow() = micTest(Mic()) { vm, mic, _, recorder ->
        vm.requestEvaluation()
        runCurrent()
        // 기기 권한 창을 기다리는 동안: 서비스 안내 창 없음, 평가도 아직 시작하지 않음.
        assertEquals(1, mic.requests)
        assertNull(vm.uiState.value.popup)
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertEquals(0, recorder.starts)

        mic.answer(true)
        runCurrent()
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        assertEquals(1, recorder.starts)
    }

    @Test fun repeatedTapsWhileAskingRequestOnceAndStartOnce() = micTest(Mic()) { vm, mic, _, recorder ->
        vm.requestEvaluation()
        runCurrent()
        vm.requestEvaluation()
        vm.requestEvaluation()
        runCurrent()
        assertEquals(1, mic.requests)

        mic.answer(true)
        runCurrent()
        vm.requestEvaluation() // 카운트다운 중 다시 눌러도 권한을 다시 묻지 않는다(이미 허용).
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(1, mic.requests)
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        assertEquals(1, recorder.starts)
    }

    @Test fun deniedButAskableStaysReadyWithoutServiceDialogAndAsksAgainOnNextTap() = micTest(Mic()) { vm, mic, _, recorder ->
        vm.requestEvaluation()
        runCurrent()
        mic.answer(false)
        runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertNull(vm.uiState.value.popup)
        assertFalse(vm.uiState.value.leaveDirect)
        assertEquals(0, recorder.starts)

        vm.requestEvaluation()
        runCurrent()
        assertEquals(2, mic.requests)
        assertNull(vm.uiState.value.popup)
    }

    @Test fun deniedInDirectEvaluationReturnsToSongInfo() = micTest(Mic(), direct = true) { vm, mic, _, recorder ->
        vm.startEvaluationOnEntry()
        runCurrent()
        assertEquals(1, mic.requests)
        mic.answer(false)
        runCurrent()
        assertTrue(vm.uiState.value.leaveDirect)
        assertNull(vm.uiState.value.popup)
        assertEquals(0, recorder.starts)
    }

    @Test fun blockedBeforeTapShowsSettingsNoticeWithoutPrompt() = micTest(Mic(blocked = true)) { vm, mic, _, recorder ->
        vm.requestEvaluation()
        runCurrent()
        assertEquals(0, mic.requests)
        assertEquals(PracticePopup.PermBlocked, vm.uiState.value.popup)
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertEquals(0, recorder.starts)
    }

    @Test fun blockedByThisPromptShowsSettingsNoticeAfterPromptCloses() = micTest(Mic()) { vm, mic, _, _ ->
        vm.requestEvaluation()
        runCurrent()
        assertNull(vm.uiState.value.popup) // 권한 창이 떠 있는 동안에는 겹쳐 띄우지 않는다.
        mic.blocked = true // Android '다시 묻지 않음'·iOS 첫 거부처럼 이번 응답으로 더 묻지 못하게 됨.
        mic.answer(false)
        runCurrent()
        assertEquals(PracticePopup.PermBlocked, vm.uiState.value.popup)
    }

    @Test fun promptFailureDoesNotStartEvaluation() = micTest(Mic(fails = true)) { vm, mic, _, recorder ->
        vm.requestEvaluation()
        runCurrent()
        assertEquals(1, mic.requests)
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertNull(vm.uiState.value.popup)
        assertEquals(0, recorder.starts)

        vm.requestEvaluation() // 실패 뒤에도 다시 누르면 다시 묻는다.
        runCurrent()
        assertEquals(2, mic.requests)
    }

    private class Mic(var granted: Boolean = false, var blocked: Boolean = false, val fails: Boolean = false) : MicPermission {
        var requests = 0
            private set
        private var pending: CompletableDeferred<Boolean>? = null

        override fun isGranted() = granted
        override fun isPermanentlyDenied() = !granted && blocked
        override suspend fun request(): Boolean {
            requests++
            if (fails) error("권한 창을 띄우지 못함")
            return CompletableDeferred<Boolean>().also { pending = it }.await()
        }

        fun answer(allow: Boolean) {
            granted = allow
            requireNotNull(pending).complete(allow)
            pending = null
        }
    }

    private fun micTest(
        mic: Mic,
        direct: Boolean = false,
        block: suspend TestScope.(PracticeViewModel, Mic, PracticeSettingsStore, FakePracticeRecorder) -> Unit,
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
            val recorder = FakePracticeRecorder()
            val vm = PracticeViewModel(song.id, false, songs, settings, PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder, mic,
                playbackTimeSource = testScheduler.timeSource, newKey = { "take-key" }, direct = direct)
            models.put("practice", vm)
            runCurrent()
            block(vm, mic, settings, recorder)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}

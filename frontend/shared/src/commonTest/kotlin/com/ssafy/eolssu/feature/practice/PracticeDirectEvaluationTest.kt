package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 곡 정보의 '연주 평가 시작'으로 들어온 연주 평가(S15P31E102-705): 대기 상태 없이 바로 시작하고, 준비로 돌아오면 곡 정보로 돌아갈 상태가 된다. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeDirectEvaluationTest {
    @Test fun directEvaluationStartsCountdownOnEntryWithoutReadyStep() = directTest { vm, settings ->
        settings.grantMic()
        assertTrue(vm.uiState.value.direct)
        vm.startEvaluationOnEntry()
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
        assertFalse(vm.leavesDirectOnReturn())
    }

    @Test fun directEvaluationAsksMicPermissionFirstWhenNotGranted() = directTest { vm, _ ->
        vm.startEvaluationOnEntry()
        assertEquals(PracticePopup.Permission, vm.uiState.value.popup)
        assertFalse(vm.leavesDirectOnReturn())
    }

    @Test fun backToReadyAfterDirectStartLeavesForSongInfo() = directTest { vm, settings ->
        settings.grantMic()
        assertFalse(vm.leavesDirectOnReturn()) // 아직 시작 전(곡 로딩 직후)에는 남는다.
        vm.startEvaluationOnEntry()
        vm.toReady() // 분석으로 넘기기 전 준비 상태로 돌린 것과 같다.
        assertTrue(vm.leavesDirectOnReturn())
    }

    @Test fun switchedEvaluationKeepsReadyScreen() = directTest(direct = false) { vm, settings ->
        settings.grantMic()
        assertFalse(vm.uiState.value.direct)
        vm.startEvaluationOnEntry()
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

    private fun directTest(
        direct: Boolean = true,
        solo: Boolean = false,
        recorder: Boolean = true,
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
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), if (recorder) FakePracticeRecorder() else null,
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

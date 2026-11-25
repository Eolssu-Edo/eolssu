package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.CatalogUnavailable
import com.ssafy.eolssu.data.song.CatalogUnreachable
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
import kotlin.test.assertNull

/** 평가 시작 전 현재 악보 확인(S15P31E102-1219): 연결 끊김은 서버 오류와 다른 안내를 보이고 ‘다시 시도’로 이어 간다. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeOfflineEvaluationTest {
    @Test fun unreachableServerShowsConnectionNoticeAndRetryStartsAfterReconnect() = offlineTest { vm, songs, recorder ->
        songs.failure = CatalogUnreachable()
        vm.requestEvaluation()
        runCurrent()
        assertEquals(PracticePopup.ScoreOffline, vm.uiState.value.popup)
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        assertEquals(0, recorder.starts)

        // 연결이 그대로면 다시 시도해도 같은 안내를 보인다.
        vm.retryEvaluation()
        runCurrent()
        assertEquals(PracticePopup.ScoreOffline, vm.uiState.value.popup)

        songs.failure = null
        vm.retryEvaluation()
        runCurrent()
        assertNull(vm.uiState.value.popup)
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
    }

    @Test fun serverErrorKeepsScoreUnavailableNotice() = offlineTest { vm, songs, recorder ->
        songs.failure = CatalogUnavailable()
        vm.requestEvaluation()
        runCurrent()
        assertEquals(PracticePopup.ScoreUnavailable, vm.uiState.value.popup)
        assertEquals(0, recorder.starts)
    }

    private class Songs : SongRepository by SampleSongRepository() {
        val song = smallScoreSong().copy(scoreVersion = 3)
        var failure: Exception? = null
        override suspend fun getSong(songId: String) = failure?.let { throw it } ?: song
    }

    private object GrantedMic : MicPermission {
        override fun isGranted() = true
        override fun isPermanentlyDenied() = false
        override suspend fun request() = true
    }

    private fun offlineTest(block: suspend TestScope.(PracticeViewModel, Songs, FakePracticeRecorder) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val songs = Songs()
            val recorder = FakePracticeRecorder()
            val vm = PracticeViewModel(songs.song.id, false, songs, PracticeSettingsStore(storage), PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder, GrantedMic,
                playbackTimeSource = testScheduler.timeSource, newKey = { "take-key" })
            models.put("practice", vm)
            runCurrent()
            block(vm, songs, recorder)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}

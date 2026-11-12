package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModelSectionPerformanceTest {
    @Test fun sectionRestartUsesPerformedTimeAndWholeScoreRestartStillUsesZero() = practice { vm, player ->
        vm.startModel()
        runCurrent()
        assertEquals(2250L, player.requestedPosition, "Tick 8 starts at 2250ms in the fixed performed timeline")
        player.emit(AudioPlaybackStatus.Playing, 2500)
        runCurrent()
        vm.restartModel()
        runCurrent()
        assertEquals(2250L, player.requestedPosition, "Restart must keep the selected section")
    }

    @Test fun wholeScoreRestartStillUsesZero() = practice(section = null) { vm, player ->
        // 구간 연습이 아닌 곡 전체 연습은 처음(0ms)부터 다시 시작한다(S15P31E102-794: 구간 연습은 연습 기록에서만 고정 구간으로 연다).
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 2500)
        runCurrent()
        vm.restartModel()
        runCurrent()
        assertEquals(0L, player.requestedPosition, "Whole-score restart keeps its original beginning")
    }

    @Test fun completedSectionCanPlayAgainFromPerformedBeginning() = practice { vm, player ->
        // 구간 연습은 구간을 한 번 듣고 구간 처음에 멈춘다(S15P31E102-794). 다시 누르면 수행표의 구간 시작부터 듣는다.
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 2250)
        runCurrent()
        player.emit(AudioPlaybackStatus.Completed, 7050)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        vm.startModel()
        runCurrent()
        assertEquals(2250L, player.requestedPosition)
    }

    @Test fun sectionEndStopsAtSectionStartWithoutPlayingAgain() = practice(section = ScoreSection(ScorePosition(0, 0), ScorePosition(0, 0))) { vm, player ->
        // S15P31E102-807: 플레이어의 멈춤 보고가 늦어도 구간 끝에서 다시 불러 틀지 않는다.
        vm.startModel()
        runCurrent()
        assertEquals(1, player.loads)
        val sectionStart = player.requestedPosition
        var position = sectionStart
        while (position < 7000L) {
            player.emit(AudioPlaybackStatus.Playing, position)
            runCurrent()
            if (!vm.uiState.value.playing) break
            position += 50
        }
        assertTrue(position < 7000L, "The section ends before the recording ends")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, player.loads, "Reaching the section end must not load the model again")
        assertFalse(state.playing)
        assertEquals(ModelAudioStatus.Paused, state.modelAudioStatus)
        assertEquals(state.sectionEvents?.first, state.position)
        vm.startModel()
        runCurrent()
        assertEquals(2, player.loads)
        assertEquals(sectionStart, player.requestedPosition, "Playing again starts from the section start")
    }

    @Test fun seekWhilePlayingBuffersWithoutLoadingOverlay() = practice(section = null) { vm, player ->
        vm.startModel()
        runCurrent()
        assertEquals(PracticePlayerNotice.Loading, vm.uiState.value.playerNotice, "The first start shows loading")
        player.emit(AudioPlaybackStatus.Playing, 1000)
        runCurrent()
        vm.seekModel(1000)
        runCurrent()
        var state = vm.uiState.value
        assertEquals(2, player.loads)
        assertEquals(ModelAudioStatus.Loading, state.modelAudioStatus)
        assertTrue(state.modelSeekBuffering)
        assertNotEquals(PracticePlayerNotice.Loading, state.playerNotice, "Seek buffering must not show loading")
        // 불러오는 동안 또 누르면 그 자리에서 다시 옮긴다.
        val firstTarget = player.requestedPosition
        vm.seekModel(1000)
        runCurrent()
        assertEquals(3, player.loads)
        assertEquals(firstTarget + 1000, player.requestedPosition)
        assertNotEquals(PracticePlayerNotice.Loading, vm.uiState.value.playerNotice)
        player.emit(AudioPlaybackStatus.Playing, player.requestedPosition)
        runCurrent()
        state = vm.uiState.value
        assertTrue(state.playing)
        assertFalse(state.modelSeekBuffering)
        // 다시 불러오는 동안의 ‘멈춤’은 멈춘다.
        vm.seekModel(-1000)
        runCurrent()
        vm.toggleModel()
        runCurrent()
        assertEquals(ModelAudioStatus.Paused, vm.uiState.value.modelAudioStatus)
        assertFalse(vm.uiState.value.playing)
    }

    @Test fun pausedSeekThenPlayStillShowsLoading() = practice(section = null) { vm, player ->
        // 멈춘 뒤 옮기고 다시 들으면 처음 재생처럼 로딩을 띄운다.
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 1000)
        runCurrent()
        vm.toggleModel()
        runCurrent()
        vm.seekModel(1000)
        runCurrent()
        vm.startModel()
        runCurrent()
        assertEquals(PracticePlayerNotice.Loading, vm.uiState.value.playerNotice)
    }

    private fun practice(
        section: ScoreSection? = ScoreSection(ScorePosition(1, 0), ScorePosition(1, 0)),
        block: suspend TestScope.(PracticeViewModel, FakeAudioPlayer) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val views = ViewModelStore()
        try {
            val song = previewSong()
            val audio = previewAudio().withPerformance(previewJson(), song, SourceHash)
            val repository = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String): Song = song
            }
            val storage = FakeKeyValueStore()
            val player = FakeAudioPlayer()
            val model = PracticeViewModel(song.id, true, repository, PracticeSettingsStore(storage),
                PracticeTimeMeter({ 0L }), PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope),
                playbackTimeSource = testScheduler.timeSource, modelAudioRepository = modelRepository { audio },
                audioPlayer = player, initialSection = section)
            views.put("practice", model)
            runCurrent()
            block(model, player)
        } finally {
            views.clear()
            Dispatchers.resetMain()
        }
    }
}

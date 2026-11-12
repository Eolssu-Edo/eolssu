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

@OptIn(ExperimentalCoroutinesApi::class)
class ModelSectionPerformanceTest {
    @Test fun sectionStopsWhenThePerformanceRepeatsBackOutsideTheSelection() = practice { vm, player ->
        vm.startModel(); runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 2250); runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 3250); runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(2250L, player.requestedPosition)
    }

    @Test fun sectionEndFollowsTheCurrentOccurrenceAndPreservesRepeatsInsideTheSection() {
        val song = previewSong()
        val audio = previewAudio().withPerformance(previewJson(), song, SourceHash)
        assertEquals(3250.0, song.modelSectionEndMediaPosition(audio, 8.0..12.0, 2250.0))
        assertEquals(6500.0, song.modelSectionEndMediaPosition(audio, 8.0..12.0, 5500.0))
        assertEquals(6500.0, song.modelSectionEndMediaPosition(audio, 0.0..12.0, 0.0))
        assertEquals(1250.0, song.modelSectionEndMediaPosition(audio, 0.0..4.0, 0.0))
    }

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

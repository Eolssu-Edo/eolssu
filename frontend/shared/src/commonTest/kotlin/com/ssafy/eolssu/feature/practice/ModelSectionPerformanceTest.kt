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
    @Test fun sectionRestartUsesPerformedTimeAndWholeScoreRestartStillUsesZero() = practice { vm, player ->
        vm.startModel()
        runCurrent()
        assertEquals(2250L, player.requestedPosition, "Tick 8 starts at 2250ms in the fixed performed timeline")
        player.emit(AudioPlaybackStatus.Playing, 2500)
        runCurrent()
        vm.restartModel()
        runCurrent()
        assertEquals(2250L, player.requestedPosition, "Restart must keep the selected section")

        vm.toggleSectionPractice()
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 2500)
        runCurrent()
        vm.restartModel()
        runCurrent()
        assertEquals(0L, player.requestedPosition, "Whole-score restart keeps its original beginning")
    }

    @Test fun completedSectionRoundsCanPlayAgainFromPerformedBeginning() = practice { vm, player ->
        vm.selectSectionRepeat(SectionRepeat.Three)
        vm.startModel()
        runCurrent()
        repeat(3) {
            player.emit(AudioPlaybackStatus.Playing, 2250)
            runCurrent()
            player.emit(AudioPlaybackStatus.Completed, 7050)
            runCurrent()
        }
        assertFalse(vm.uiState.value.playing)
        assertEquals(3, vm.uiState.value.sectionPractice?.round)
        vm.startModel()
        runCurrent()
        assertEquals(2250L, player.requestedPosition)
    }

    private fun practice(block: suspend TestScope.(PracticeViewModel, FakeAudioPlayer) -> Unit) = runTest {
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
                audioPlayer = player, initialSection = ScoreSection(ScorePosition(1, 0), ScorePosition(1, 0)))
            views.put("practice", model)
            runCurrent()
            block(model, player)
        } finally {
            views.clear()
            Dispatchers.resetMain()
        }
    }
}

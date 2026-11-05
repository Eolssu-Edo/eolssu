package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.data.song.audioFixture
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.ui.score.ScoreCursor
import com.ssafy.eolssu.feature.practice.component.scorePlayback
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreData
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
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeLiveProgressTest {
    @Test fun timelineSelectionBeforePlaybackKeepsExactPositionWithoutStartingAudio() = progressTest { vm, player ->
        vm.seekToFraction(0.5f)
        runCurrent()
        assertEquals(50f, vm.uiState.value.progressPercent, 0.001f)
        assertFalse(vm.uiState.value.playing)
        vm.startModel()
        runCurrent()
        assertEquals(625L, player.requestedPosition)
    }

    @Test fun timelineSeekWhilePlayingContinuesFromChosenPosition() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 250)
        runCurrent()
        vm.seekToFraction(0.8f)
        runCurrent()
        assertEquals(1000L, player.requestedPosition)
        player.emit(AudioPlaybackStatus.Playing, 1000)
        runCurrent()
        assertTrue(vm.uiState.value.playing)
        assertEquals(80f, vm.uiState.value.progressPercent, 0.001f)
    }

    @Test fun timelineSeekWhilePausedStaysPausedAndResumesFromChosenPosition() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 250)
        runCurrent()
        vm.toggleModel()
        runCurrent()
        vm.seekToFraction(0.5f)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(50f, vm.uiState.value.progressPercent, 0.001f)
        vm.startModel()
        runCurrent()
        assertEquals(625L, player.requestedPosition)
    }

    @Test fun playAfterCompletionRestartsAtTheBeginning() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Completed, 1250)
        runCurrent()
        assertEquals(100f, vm.uiState.value.progressPercent)
        vm.startModel()
        runCurrent()
        assertEquals(0L, player.requestedPosition)
    }

    @Test fun decodedPositionMovesTheBeatWithoutGeneratingAnotherNote() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        player.emit(AudioPlaybackStatus.Playing, 625)
        runCurrent()
        val state = vm.uiState.value
        assertEquals(1, state.beatIndex)
        assertEquals(0, state.position)
        assertEquals("c0-j0-r0-u0", state.song?.timeline?.get(0)?.id)
        assertEquals(0, state.song?.timeline?.get(0)?.noteIndex)
        assertEquals(625.0, state.elapsedMs, 0.0001)
        assertEquals(3.0, requireNotNull(state.currentTick), 0.0001)
        assertEquals(1, state.currentScoreUnit?.row)
        assertEquals(50f, state.progressPercent, 0.0001f)
    }

    @Test fun pausingDoesNotAdvanceWithoutANewDecodedPosition() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 700)
        runCurrent()
        vm.toggleModel()
        runCurrent()
        val paused = vm.uiState.value
        assertFalse(paused.playing)
        assertEquals(700.0, paused.elapsedMs, 0.0001)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(paused.currentTick, vm.uiState.value.currentTick)
        vm.toggleModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 1042)
        runCurrent()
        assertEquals(1, vm.uiState.value.position)
        assertEquals("c0-j1-r2-u0", vm.uiState.value.song?.timeline?.get(1)?.id)
        player.emit(AudioPlaybackStatus.Completed, 1250)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(1250.0, vm.uiState.value.elapsedMs, 0.0001)
        assertEquals(100f, vm.uiState.value.progressPercent)
    }

    @Test fun hiddenScreenReleasesAudioAndPreservesTheLastEventPosition() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 1100)
        runCurrent()
        vm.onScreenHidden()
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(AudioPlaybackStatus.Idle, player.state.value.status)
        advanceTimeBy(2000)
        vm.toggleModel()
        runCurrent()
        assertEquals(1100L, player.requestedPosition)
        player.emit(AudioPlaybackStatus.Playing, 1100)
        runCurrent()
        assertEquals(1, vm.uiState.value.position)
        assertTrue(vm.uiState.value.playing)
    }

    @Test fun tempoChangeUpdatesPlayerRateAndKeepsOriginalTickPosition() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 625)
        runCurrent()
        vm.openPanel(PracticePanel.Tempo)
        vm.updateDraft { it.copy(step = TempoStep.Slow) }
        vm.applyTempo()
        runCurrent()
        assertEquals(77, vm.uiState.value.bpm)
        assertEquals(77f / 96f, player.requestedSpeed)
        assertEquals(3.0, requireNotNull(vm.uiState.value.currentTick), 0.0001)
        player.emit(AudioPlaybackStatus.Completed, 1250)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(100f, vm.uiState.value.progressPercent)
    }

    @Test fun recordingAbortAndResumeCountdownDoNotAdvanceTheScoreClock() = progressTest { vm, _ ->
        // 실제 마이크 대신 녹음 대역을 사용해 녹음 화면의 시계/상태를 검사한다.
        vm.grantMicAndStart()
        runCurrent()
        advanceTimeBy(3000)
        runCurrent()
        advanceTimeBy(625)
        runCurrent()
        vm.askAbort()
        assertEquals(1, vm.uiState.value.beatIndex)
        assertEquals(625.0, vm.uiState.value.elapsedMs, 0.0001)
        advanceTimeBy(1500)
        vm.resumeRecording()
        runCurrent()
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(625.0, vm.uiState.value.elapsedMs, 0.0001)
        assertEquals(3.0, requireNotNull(vm.uiState.value.currentTick), 0.0001)
        advanceTimeBy(417)
        runCurrent()
        assertEquals(1, vm.uiState.value.position)
        assertEquals(1042.0, vm.uiState.value.elapsedMs, 0.0001)
    }

    @Test fun modelPlayheadKeepsMovingBetweenPositionReportsAndCrossesIntoTheNextNote() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 625)
        runCurrent()
        val playback = requireNotNull(vm.uiState.value.scorePlayback())
        assertTrue(playback.running)
        assertEquals(ScoreCursor(0, 0.6f), playback.at())
        // 위치 보고 없이도 시간만큼 이어 가고, 첫 음(5 tick ≈ 1042ms)이 끝나면 화면이 직접 다음 음으로 넘어간다.
        advanceTimeBy(208)
        assertEquals(0.8f, playback.at().fraction, 0.001f)
        advanceTimeBy(292)
        assertEquals(1, playback.at().index)
    }

    @Test fun modelPlayheadNeverMovesBackOnJitteryPositionReports() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 100)
        runCurrent()
        var last = 0.0
        // 실제 위치는 50ms마다 50ms씩 가지만 보고는 ±30ms 흔들린다.
        for ((step, jitter) in listOf(30, -30, 25, -25, 30, -30, 20, -20).withIndex()) {
            advanceTimeBy(50)
            player.emit(AudioPlaybackStatus.Playing, 100L + 50 * (step + 1) + jitter)
            runCurrent()
            val clock = requireNotNull(vm.uiState.value.modelClock)
            val shown = clock.anchor.positionNow()
            assertTrue(shown >= last, "step $step: $shown < $last")
            assertTrue(clock.anchor.rate in 0.9..1.1, "step $step: rate ${clock.anchor.rate}")
            last = shown
        }
        assertEquals(500.0, last, 30.0)
    }

    @Test fun modelPlayheadJumpsToAFarSeekAndStopsWhenPaused() = progressTest { vm, player ->
        vm.startModel()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 100)
        runCurrent()
        advanceTimeBy(50)
        player.emit(AudioPlaybackStatus.Playing, 1100)
        runCurrent()
        assertEquals(1100.0, requireNotNull(vm.uiState.value.modelClock).anchor.positionNow(), 0.001)
        vm.toggleModel()
        runCurrent()
        assertEquals(null, vm.uiState.value.modelClock)
        val paused = requireNotNull(vm.uiState.value.scorePlayback())
        assertFalse(paused.running)
        assertEquals(1, paused.at().index)
    }

    @Test fun changingAccountStopsModelPlaybackAndPreventsOldScreenRestart() = run {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }
        progressTest(member) { vm, player ->
            vm.startModel()
            runCurrent()
            player.emit(AudioPlaybackStatus.Playing, 625)
            runCurrent()
            val playback = requireNotNull(vm.uiState.value.scorePlayback())
            advanceTimeBy(208)
            assertEquals(0.8f, playback.at().fraction, 0.001f)
            assertEquals(1, player.state.subscriptionCount.value)
            val loadsBeforeSwitch = player.loads
            assertEquals(1, loadsBeforeSwitch)

            member.set("B")
            runCurrent()
            assertEquals(AudioPlaybackStatus.Idle, player.state.value.status)
            assertEquals(0, player.state.subscriptionCount.value)
            assertEquals(null, vm.uiState.value.modelClock)
            assertEquals(null, vm.uiState.value.song)
            assertFalse(vm.uiState.value.playing)

            vm.startModel()
            vm.toggleModel()
            runCurrent()
            assertEquals(loadsBeforeSwitch, player.loads)
            assertEquals(AudioPlaybackStatus.Idle, player.state.value.status)
        }
    }

    @Test fun recordingPlayheadChangesNoteAndFractionTogether() = progressTest { vm, _ ->
        vm.grantMicAndStart()
        runCurrent()
        advanceTimeBy(3000)
        runCurrent()
        advanceTimeBy(1000)
        val first = requireNotNull(vm.uiState.value.scorePlayback())
        assertEquals(0, first.at().index)
        assertEquals(0.96f, first.at().fraction, 0.01f)
        advanceTimeBy(100)
        runCurrent()
        // 첫 음이 끝나면 새 음은 처음부터 그린다. 옛 음의 비율(≈1)로 새 음을 그리지 않는다.
        val next = requireNotNull(vm.uiState.value.scorePlayback()).at()
        assertEquals(1, next.index)
        assertTrue(next.fraction < 0.5f, "fraction ${next.fraction}")
    }

    @Test fun lateWakeupCarriesExcessIntoTheNextEvent() {
        val source = TestTimeSource()
        val clock = PracticeTimelineClock(source, carriedEventMs = 250.0, carriedElapsedMs = 1000.0)
        source += 1000.milliseconds
        assertEquals(TimelineSample(1250.0, 2000.0), clock.sample())
        clock.completeEvent(1000.0)
        assertEquals(TimelineSample(250.0, 2000.0), clock.sample())
        assertEquals(250.0, clock.eventStartedAt.elapsedNow().inWholeNanoseconds / 1_000_000.0)
    }

    private fun progressTest(
        member: SignedInMember? = null,
        block: suspend TestScope.(PracticeViewModel, FakeAudioPlayer) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val song = arirangOpening().copy(id = "42", scoreVersion = 1)
            val player = FakeAudioPlayer()
            val repository = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String): Song = song
            }
            val settings = FakeKeyValueStore()
            val vm = PracticeViewModel(song.id, false, repository, PracticeSettingsStore(settings),
                PracticeTimeMeter({ 0L }), PracticeTimeQueue(settings, null, member ?: SignedInMember(settings), backgroundScope), recorder = FakePracticeRecorder(), playbackTimeSource = testScheduler.timeSource,
                modelAudioRepository = modelRepository { audioFixture().copy(bpm = 96, durationMs = 1800) }, audioPlayer = player, member = member)
            store.put("practice", vm)
            runCurrent()
            block(vm, player)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

/** 아리랑 원본 첫 두 정간: 첫 음 5 tick, 첫 박 3 tick, BPM 96. */
private fun arirangOpening(): Song {
    val base = smallScoreData()
    val first = "c0-j0-r0-u0"
    val second = "c0-j1-r2-u0"
    val source = base.copy(
        music = base.music.copy(ticksPerBeat = 3, ticksPerJeonggan = 3),
        columns = listOf(CommonScoreData.Column(0, listOf(
            CommonScoreData.Cell("c0-j0", 0, listOf(CommonScoreData.Unit(0, 0, "중", first))),
            CommonScoreData.Cell("c0-j1", 1, listOf(CommonScoreData.Unit(0, 0, "-", first), CommonScoreData.Unit(1, 0, "-", first), CommonScoreData.Unit(2, 0, "임", second))),
        ))),
        events = listOf(
            CommonScoreData.Event(first, "NOTE", "중", 0, 0, 5, "c0-j0", false, emptyList()),
            CommonScoreData.Event(second, "NOTE", "임", 0, 5, 1, "c0-j1", false, emptyList()),
        ),
    )
    return smallScoreSong(source).copy(baseBpm = 96)
}

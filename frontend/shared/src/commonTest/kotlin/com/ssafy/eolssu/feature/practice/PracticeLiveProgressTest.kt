package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
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
    @Test fun firstArirangTieMovesToSecondBeatBeforeTheEventEnds() = progressTest { vm ->
        vm.startModel() // 실제 오디오가 아닌 기존 위치 재생 시뮬레이션이다.
        runCurrent()
        advanceTimeBy(624)
        runCurrent()
        assertEquals(0, vm.uiState.value.beatIndex)
        advanceTimeBy(1)
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

    @Test fun pausingInsideATiePreservesFractionAndExcludesPausedTime() = progressTest { vm ->
        vm.startModel()
        runCurrent()
        advanceTimeBy(700)
        runCurrent()
        vm.toggleModel()
        val paused = vm.uiState.value
        assertFalse(paused.playing)
        assertEquals(700.0, paused.elapsedMs, 0.0001)
        assertEquals(3.36, requireNotNull(paused.currentTick), 0.0001)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(paused.elapsedMs, vm.uiState.value.elapsedMs)
        assertEquals(paused.currentTick, vm.uiState.value.currentTick)
        vm.toggleModel()
        runCurrent()
        advanceTimeBy(342)
        runCurrent()
        val next = vm.uiState.value
        assertEquals(1, next.position)
        assertEquals("c0-j1-r2-u0", next.song?.timeline?.get(next.position)?.id)
        assertEquals(1, next.song?.timeline?.get(next.position)?.noteIndex)
        assertEquals(1042.0, next.elapsedMs, 0.0001)
        advanceTimeBy(208)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(1250.0, vm.uiState.value.elapsedMs, 0.0001)
        assertEquals(100f, vm.uiState.value.progressPercent)
    }

    @Test fun pausingDuringLastEventResumesItInsteadOfRestartingTheSong() = progressTest { vm ->
        vm.startModel()
        runCurrent()
        advanceTimeBy(1100)
        runCurrent()
        vm.toggleModel()
        assertEquals(1, vm.uiState.value.position)
        advanceTimeBy(2000)
        vm.toggleModel()
        runCurrent()
        assertEquals(1, vm.uiState.value.position)
        assertTrue(vm.uiState.value.playing)
        advanceTimeBy(150)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(1250.0, vm.uiState.value.elapsedMs, 0.0001)
    }

    @Test fun tempoChangePreservesMusicalPositionAndKeepsElapsedActiveTime() = progressTest { vm ->
        vm.startModel()
        runCurrent()
        advanceTimeBy(625)
        runCurrent()
        vm.openPanel(PracticePanel.Tempo)
        vm.updateDraft { it.copy(step = TempoStep.Slow) }
        vm.applyTempo()
        runCurrent()
        assertEquals(77, vm.uiState.value.bpm)
        assertEquals(3.0, requireNotNull(vm.uiState.value.currentTick), 0.0001)
        assertEquals(625.0, vm.uiState.value.elapsedMs, 0.0001)
        advanceTimeBy(780)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(625.0 + 60_000.0 / 77, vm.uiState.value.elapsedMs, 0.0001)
        assertEquals(100f, vm.uiState.value.progressPercent)
    }

    @Test fun recordingAbortAndResumeCountdownDoNotAdvanceTheScoreClock() = progressTest { vm ->
        // 마이크 입력 검사가 아닌 녹음 화면의 시계/상태 검사다. 이 테스트에는 AudioRecorder가 없다.
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

    @Test fun lateWakeupCarriesExcessIntoTheNextEvent() {
        val source = TestTimeSource()
        val clock = PracticeTimelineClock(source, carriedEventMs = 250.0, carriedElapsedMs = 1000.0)
        source += 1000.milliseconds
        assertEquals(TimelineSample(1250.0, 2000.0), clock.sample())
        clock.completeEvent(1000.0)
        assertEquals(TimelineSample(250.0, 2000.0), clock.sample())
        assertEquals(250.0, clock.eventStartedAt.elapsedNow().inWholeNanoseconds / 1_000_000.0)
    }

    private fun progressTest(block: suspend TestScope.(PracticeViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val song = arirangOpening()
            val repository = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String): Song = song
            }
            val settings = FakeKeyValueStore()
            val vm = PracticeViewModel(song.id, false, repository, PracticeSettingsStore(settings),
                PracticeTimeMeter({ 0L }), PracticeTimeQueue(settings, null, com.ssafy.eolssu.core.network.SignedInMember(settings), backgroundScope), playbackTimeSource = testScheduler.timeSource)
            store.put("practice", vm)
            runCurrent()
            block(vm)
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

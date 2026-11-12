package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.practice.PreviousRecord
import com.ssafy.eolssu.data.practice.RecordComparison
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.data.practice.WeakSectionPick
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.data.practice.WeakSectionSource
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.feature.practice.component.scorePlayback
import com.ssafy.eolssu.testing.CatalogEvaluations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구간 재평가(S15P31E102-824): 연주 평가와 같은 화면 상태·진행으로 고른 구간만 녹음한다.
 * 평가 대기 → 마이크·판본 확인 → 3·2·1 → 구간 첫 음부터 마지막 음까지 녹음 → ‘연주를 마쳤어요’ → 녹음과 구간 음 번호·바탕 세션을 분석에 넘긴다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SectionEvaluationTest {
    /** 서버 곡처럼 율명 음마다 음 번호(noteIndex)를 붙인 아리랑. 예시 악보에는 음 번호가 없다. */
    private val song = SampleSongs.first { it.id == "arirang" }.let { base ->
        var noteIndex = 0
        base.copy(score = base.score.copy(events = base.timeline.map { if (it.note is ScoreNote.Pitch) it.copy(noteIndex = noteIndex++) else it }))
    }
    private val section = ScoreSection.ofColumns(song.score, 1, 2)
    private val sectionEvents = song.timeline.filter { section.contains(it.column, it.row) }.let { it.first().index..it.last().index }

    private open class Source(private val song: Song) : WeakSectionRepository {
        override suspend fun source(sourceId: String): WeakSectionSource? = WeakSectionSource(song, checkNotNull(CatalogEvaluations().cached))
        override suspend fun weakSections(sessionId: String): List<WeakSectionPick>? = null
        override suspend fun sectionResult(sessionId: String): SectionEvaluation? = null
        override suspend fun comparison(recordId: String): RecordComparison? = null
        override suspend fun previous(currentId: String, previousId: String): PreviousRecord? = null
    }

    private class Mic(var granted: Boolean, val allow: Boolean = granted, val blocked: Boolean = false) : MicPermission {
        var requests = 0
        override fun isGranted() = granted
        override suspend fun request(): Boolean { requests++; granted = allow; return allow }
        override fun isPermanentlyDenied() = blocked
    }

    @Test fun waitsOnTheEvaluationScreenWithTheSectionMarked() = sectionTest { vm, _ ->
        val state = vm.uiState.value
        assertEquals(song, state.song)
        // 연주 평가와 같은 평가 대기 상태(하단 평가 막대 · ‘평가 시작’)다.
        assertTrue(state.direct)
        assertTrue(state.awaitingStart)
        assertTrue(state.recordingLayout)
        // 고른 구간을 표시하고 악보를 구간 처음에 둔다.
        assertTrue(state.sectionEvaluation)
        assertEquals(section, state.sectionPractice?.section)
        assertEquals(sectionEvents, state.sectionEvents)
        assertEquals(sectionEvents.first, state.sectionReadyStart)
        // 속도는 연습 화면과 같은 곡 속도 설정을 따르고 녹음 전에는 설정에서 바꾼다.
        assertFalse(state.playbackSettingsLocked)
    }

    @Test fun tempoFollowsTheSavedSongTempoUntilRecordingStarts() = sectionTest(prepare = { settings ->
        settings.update { it.copy(bpmBySong = it.bpmBySong + (song.id to 42)) }
    }) { vm, _ ->
        assertEquals(42, vm.uiState.value.bpm)
        vm.openPanel(PracticePanel.Tempo)
        vm.updateDraft { it.copy(step = TempoStep.Slowest) }
        runCurrent()
        assertEquals(TempoStep.Slowest.bpm(song.baseBpm), vm.uiState.value.bpm)
        vm.dismissPanels()
        // 3·2·1과 녹음 중에는 연주 평가처럼 속도가 잠긴다.
        vm.requestEvaluation()
        runCurrent()
        assertTrue(vm.uiState.value.playbackSettingsLocked)
    }

    @Test fun countdownKeepsTheScoreAtTheSectionStart() = sectionTest { vm, _ ->
        vm.stopFollowing()
        vm.requestEvaluation()
        runCurrent()
        val state = vm.uiState.value
        assertEquals(PracticeMode.Countdown, state.mode)
        assertTrue(state.preparing)
        // 곡 전체 평가는 악보를 곡 처음으로 되돌리지만, 구간 재평가는 구간 처음을 보인 채로 센다.
        assertEquals(sectionEvents.first, state.sectionReadyStart)
        assertTrue(state.follow)
    }

    @Test fun recordsOnlyTheSectionAndHandsTheTakeWithNoteRangeAndParent() {
        val recorder = FakePracticeRecorder(RecordedAudio("section.m4a", 2048))
        sectionTest(recorder = recorder) { vm, _ ->
            vm.requestEvaluation()
            advanceTimeBy(3_000)
            runCurrent()
            // 3·2·1 뒤 구간 첫 음에서 시작한다. 시계는 곡 처음 기준으로 구간이 시작하는 시간부터 간다.
            val started = vm.uiState.value
            assertEquals(PracticeMode.Recording, started.mode)
            assertEquals(1, recorder.starts)
            assertEquals(sectionEvents.first, started.position)
            assertEquals(started.eventStartMs(sectionEvents.first), started.recordingStartMs)
            assertTrue(started.recordingStartMs > 0.0)
            assertTrue(started.recordingElapsedSeconds < 0.05)
            // 연주 평가와 같은 진행 모습(악보의 진행 채움)을 구간 첫 음부터 그린다.
            assertEquals(sectionEvents.first, assertNotNull(started.scorePlayback()).at().index)

            // 가운데쯤: 하단 막대의 시간과 진행 막대는 구간 안에서 잰다.
            val totalMs = started.recordingTotalSeconds * 1000
            advanceTimeBy((totalMs / 2).toLong())
            runCurrent()
            val middle = vm.uiState.value
            assertTrue(middle.position in sectionEvents)
            assertTrue(abs(middle.recordingElapsedSeconds - totalMs / 2000) < 0.1, "elapsed=${middle.recordingElapsedSeconds}")
            assertTrue(abs(middle.recordingProgress - 0.5f) < 0.05f, "progress=${middle.recordingProgress}")
            assertNull(middle.popup)

            // 구간 마지막 음에서 끝난다(곡 끝까지 가지 않는다). 연주 평가처럼 ‘연주를 마쳤어요’를 띄운다.
            advanceTimeBy((totalMs / 2).toLong() + 100)
            runCurrent()
            val done = vm.uiState.value
            assertTrue(done.finished)
            assertEquals(PracticePopup.End, done.popup)
            assertEquals(sectionEvents.last + 1, done.position)
            assertTrue(done.position < done.eventCount)
            assertEquals(1f, done.recordingProgress)

            // ‘평가하기’: 녹음과 구간 음 번호·바탕 세션을 넘긴다.
            val take = assertNotNull(vm.takeForEvaluation())
            assertEquals("section.m4a" to "take-key", take.path to take.key)
            val notes = checkNotNull(noteRange(song, section))
            assertEquals(SessionSection(notes.first, notes.last, "30"), vm.sectionForEvaluation())
            // 결과에서 돌아오면 같은 구간을 다시 평가할 수 있게 평가 대기 상태로 남는다.
            vm.toReady()
            assertFalse(vm.leavesDirectOnReturn())
            assertTrue(vm.uiState.value.awaitingStart)
        }
    }

    @Test fun sectionTakeCarriesTheRevisionUsedByTheRecorder() = sectionTest(repository = Source(song.copy(scoreVersion = 1))) { vm, _ ->
        vm.requestEvaluation()
        advanceTimeBy(3_000)
        runCurrent()
        advanceTimeBy((vm.uiState.value.recordingTotalSeconds * 1000).toLong() + 100)
        runCurrent()
        assertEquals(1, vm.takeForEvaluation()?.scoreVersion)
    }

    @Test fun abortPausesTheRecordingAndKeepPlayingResumesFromTheSamePlace() {
        val recorder = FakePracticeRecorder(RecordedAudio("section.m4a", 2048))
        sectionTest(recorder = recorder) { vm, _ ->
            vm.requestEvaluation()
            advanceTimeBy(3_000)
            runCurrent()
            val totalMs = vm.uiState.value.recordingTotalSeconds * 1000
            advanceTimeBy((totalMs / 3).toLong())
            runCurrent()

            // 연주 평가처럼 ‘연주 중단’은 녹음·진행을 그 자리에서 멈추고 묻는다. 묻는 동안 끝나 넘어가면 안 된다.
            vm.askAbort()
            val asked = vm.uiState.value
            assertEquals(PracticePopup.Abort, asked.popup)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(asked.position, vm.uiState.value.position)
            assertEquals(asked.recordingElapsedSeconds, vm.uiState.value.recordingElapsedSeconds)
            assertFalse(vm.uiState.value.finished)

            // ‘계속 연주’: 3·2·1 뒤 멈춘 자리부터 이어 구간 끝까지 녹음한다.
            vm.resumeRecording()
            assertTrue(vm.uiState.value.resuming)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
            assertTrue(vm.uiState.value.position >= asked.position)
            advanceTimeBy(totalMs.toLong())
            runCurrent()
            assertEquals(PracticePopup.End, vm.uiState.value.popup)
            assertEquals(1, recorder.starts)
            assertNotNull(vm.takeForEvaluation())
        }
    }

    @Test fun waitingAfterAbortDropsTheRecording() {
        val recorder = FakePracticeRecorder()
        sectionTest(recorder = recorder) { vm, _ ->
            vm.requestEvaluation()
            advanceTimeBy(4_000)
            runCurrent()
            vm.askAbort()
            val cancelsBefore = recorder.cancels
            // ‘연주 대기로’
            vm.toReady()
            runCurrent()
            assertTrue(vm.uiState.value.awaitingStart)
            assertNull(vm.uiState.value.popup)
            assertTrue(recorder.cancels > cancelsBefore)
            assertNull(vm.takeForEvaluation())
        }
    }

    @Test fun leavingTheScreenWhileRecordingAsksLikeTheWholeSongEvaluation() = sectionTest { vm, _ ->
        vm.requestEvaluation()
        advanceTimeBy(4_000)
        runCurrent()
        // 뒤로 가기: 나갈지 묻고 화면에 머문다.
        assertFalse(vm.onBack())
        assertEquals(PracticePopup.Exit, vm.uiState.value.popup)
        // 앱을 벗어나면 끊긴 연주로 처리한다.
        vm.onLeaveScreen()
        assertEquals(PracticeMode.Interrupted, vm.uiState.value.mode)
        assertEquals(PracticePopup.Interrupted, vm.uiState.value.popup)
        assertNull(vm.takeForEvaluation())
    }

    @Test fun changedRevisionCannotStartASectionOrOpenTheMicrophone() {
        val stale = object : Source(song) {
            override suspend fun isCurrent(source: WeakSectionSource) = false
        }
        val recorder = FakePracticeRecorder()
        val mic = Mic(granted = false, allow = true)
        sectionTest(repository = stale, recorder = recorder, mic = mic) { vm, _ ->
            val select = SectionSelectViewModel("30", stale)
            try {
                runCurrent()
                select.tap(section.start)
                select.tap(section.end)
                assertFalse(select.uiState.value.canStart)
            } finally {
                ViewModelStore().apply { put("select", select) }.clear()
            }
            vm.requestEvaluation()
            runCurrent()
            assertEquals(PracticePopup.ScoreChanged, vm.uiState.value.popup)
            assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
            assertEquals(0, mic.requests)
            assertEquals(0, recorder.starts)
        }
    }

    @Test fun unavailableCurrentScoreCannotOpenTheMicrophone() {
        val unavailable = object : Source(song) {
            override suspend fun isCurrent(source: WeakSectionSource): Boolean? = null
        }
        val recorder = FakePracticeRecorder()
        val mic = Mic(granted = false, allow = true)
        sectionTest(repository = unavailable, recorder = recorder, mic = mic) { vm, _ ->
            vm.requestEvaluation()
            runCurrent()
            assertEquals(PracticePopup.ScoreUnavailable, vm.uiState.value.popup)
            assertEquals(0, mic.requests)
            assertEquals(0, recorder.starts)
        }
    }

    @Test fun sectionWithoutPitchNotesExplainsInsteadOfRecording() {
        // 음 번호가 없는 악보(쉼표만 고른 구간과 같다)는 분석에 넘길 구간이 없다.
        val recorder = FakePracticeRecorder()
        sectionTest(repository = Source(SampleSongs.first { it.id == "arirang" }), recorder = recorder) { vm, _ ->
            assertNull(vm.sectionForEvaluation())
            vm.requestEvaluation()
            runCurrent()
            assertEquals(PracticePopup.NoSectionNotes, vm.uiState.value.popup)
            assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
            assertEquals(0, recorder.starts)
        }
    }

    @Test fun deviceWithoutRecorderKeepsTheEvaluationScreenButCannotStart() = sectionTest(recorder = null) { vm, _ ->
        // 녹음기가 없는 플랫폼(웹)도 구간 재평가 화면을 보이고 ‘평가 시작’만 잠근다.
        assertTrue(vm.uiState.value.direct)
        assertFalse(vm.uiState.value.recordingSupported)
        vm.requestEvaluation()
        runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
    }

    @Test fun deniedMicStaysWaitingAndBlockedMicPointsToSettings() {
        val recorder = FakePracticeRecorder()
        sectionTest(recorder = recorder, mic = Mic(granted = false, allow = false)) { vm, _ ->
            vm.requestEvaluation()
            runCurrent()
            assertTrue(vm.uiState.value.awaitingStart)
            assertNull(vm.uiState.value.popup)
        }
        sectionTest(recorder = recorder, mic = Mic(granted = false, allow = false, blocked = true)) { vm, _ ->
            vm.requestEvaluation()
            runCurrent()
            assertEquals(PracticePopup.PermBlocked, vm.uiState.value.popup)
        }
        assertEquals(0, recorder.starts)
    }

    @Test fun askingMicOnFirstEvaluationThenRecords() {
        val mic = Mic(granted = false, allow = true)
        val recorder = FakePracticeRecorder()
        sectionTest(recorder = recorder, mic = mic) { vm, _ ->
            vm.requestEvaluation()
            runCurrent()
            assertEquals(1, mic.requests)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
            assertEquals(1, recorder.starts)
        }
    }

    @Test fun unsavedRecordingIsReportedLikeTheWholeSongEvaluation() = sectionTest(recorder = FakePracticeRecorder(output = null)) { vm, _ ->
        vm.requestEvaluation()
        advanceTimeBy(3_000)
        runCurrent()
        advanceTimeBy((vm.uiState.value.recordingTotalSeconds * 1000).toLong() + 100)
        runCurrent()
        assertEquals(PracticePopup.RecordFailed, vm.uiState.value.popup)
        assertNull(vm.takeForEvaluation())
    }

    @Test fun noteRangeCountsOnlyPitchNotesInsideSection() {
        val notes = checkNotNull(noteRange(song, section))
        val inside = song.timeline.filter { it.noteIndex != null && section.contains(it.column, it.row) }.map { it.noteIndex!! }
        assertEquals(inside.min()..inside.max(), notes)
    }

    @Test fun serverPickBecomesWholeColumnsAndOutOfScoreIsIgnored() {
        assertEquals(ScoreSection.ofColumns(song.score, 1, 2), WeakSectionPick(1, 2, 5).toSection(song.score))
        assertNull(WeakSectionPick(0, song.score.columns.size, 5).toSection(song.score))
        assertNull(WeakSectionPick(3, 2, 5).toSection(song.score))
    }

    private fun sectionTest(
        repository: WeakSectionRepository = Source(song),
        recorder: FakePracticeRecorder? = FakePracticeRecorder(),
        mic: MicPermission? = Mic(granted = true),
        prepare: (PracticeSettingsStore) -> Unit = {},
        block: suspend TestScope.(PracticeViewModel, PracticeSettingsStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage).also(prepare)
            // 구간 재평가는 곡을 따로 받지 않고 바탕 세션의 악보를 쓴다.
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String): Song? = null
            }
            val vm = PracticeViewModel("", false, songs, settings, PracticeTimeMeter({ 0L }, testScheduler.timeSource),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder, mic,
                playbackTimeSource = testScheduler.timeSource, newKey = { "take-key" }, direct = true,
                initialSection = section, sectionSource = SectionEvaluationSource("30", repository))
            models.put("section-evaluation", vm)
            runCurrent()
            block(vm, settings)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}

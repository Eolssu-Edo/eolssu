package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.audio.RecordingSpec
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.PreviousRecord
import com.ssafy.eolssu.data.practice.RecordComparison
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.data.practice.WeakSectionPick
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.data.practice.WeakSectionSource
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.testing.CatalogEvaluations
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 구간 반복의 ‘이 구간 재평가’: 마이크 확인 → 3·2·1 → 구간 한 번 녹음 → 녹음과 구간 음 번호·바탕 세션을 분석에 넘긴다. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SectionLoopRecordingTest : CatalogViewModelTest() {
    /** 서버 곡처럼 율명 음마다 음 번호(noteIndex)를 붙인 아리랑. 예시 악보에는 음 번호가 없다. */
    private val song = SampleSongs.first { it.id == "arirang" }.let { base ->
        var noteIndex = 0
        base.copy(score = base.score.copy(events = base.timeline.map { if (it.note is ScoreNote.Pitch) it.copy(noteIndex = noteIndex++) else it }))
    }
    private val section = ScoreSection.ofColumns(song.score, 1, 2)

    private val source = object : WeakSectionRepository {
        override suspend fun source(sourceId: String) = WeakSectionSource(song, checkNotNull(CatalogEvaluations().cached))
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

    private fun model(recorder: FakePracticeRecorder? = FakePracticeRecorder(), mic: Mic? = Mic(granted = true)) =
        keep(SectionLoopViewModel("30", section, bpm = 60, repeats = 1, repository = source, recorder = recorder, micPermission = mic, newKey = { "take-key" }))

    @Test fun changedRevisionCannotStartASectionOrOpenTheMicrophone() = runTest {
        val stale = object : WeakSectionRepository by source {
            override suspend fun isCurrent(source: WeakSectionSource) = false
        }
        val select = keep(SectionSelectViewModel("30", stale))
        val recorder = FakePracticeRecorder()
        val mic = Mic(granted = false, allow = true)
        val loop = keep(SectionLoopViewModel("30", section, 60, 1, stale, recorder, mic))
        advanceUntilIdle()
        select.tap(section.start)
        select.tap(section.end)
        assertFalse(select.uiState.value.canStart)
        loop.startEvaluation()
        advanceUntilIdle()
        assertEquals(0, mic.requests)
        assertEquals(0, recorder.starts)
        assertNull(loop.uiState.value.take)
    }

    @Test fun unavailableCurrentScoreCannotOpenTheMicrophone() = runTest {
        val unavailable = object : WeakSectionRepository by source {
            override suspend fun isCurrent(source: WeakSectionSource): Boolean? = null
        }
        val recorder = FakePracticeRecorder()
        val mic = Mic(granted = false, allow = true)
        val loop = keep(SectionLoopViewModel("30", section, 60, 1, unavailable, recorder, mic))
        advanceUntilIdle()
        loop.startEvaluation()
        advanceUntilIdle()
        assertEquals(SectionLoopNotice.ScoreUnavailable, loop.uiState.value.notice)
        assertEquals(0, mic.requests)
        assertEquals(0, recorder.starts)
    }

    @Test fun sectionScreensWaitForAPressInsteadOfStartingTheLoop() = runTest {
        // 구간 연습·구간 재평가 화면은 연습·연주 평가 화면처럼 누를 때까지 기다린다(S15P31E102-776).
        val model = keep(SectionLoopViewModel("30", section, 60, 1, source, FakePracticeRecorder(), Mic(granted = true), autoStart = false))
        advanceUntilIdle()
        val state = model.uiState.value
        assertFalse(state.loading)
        assertNull(state.countdown)
        assertFalse(state.running)
        assertFalse(state.recording)
    }

    @Test fun cancellingTheEvaluationDropsTheRecordingAndReturnsToWaiting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = FakePracticeRecorder(RecordedAudio("section.m4a", 2048))
        val model = keep(SectionLoopViewModel("30", section, 60, 1, source, recorder, Mic(granted = true), newKey = { "k" }, autoStart = false))
        advanceUntilIdle()
        model.startEvaluation()
        runCurrent()
        assertTrue(model.uiState.value.recording)
        model.cancelEvaluation()
        advanceUntilIdle()
        val state = model.uiState.value
        assertFalse(state.recording)
        assertNull(state.countdown)
        assertNull(state.take)
        assertEquals(0, recorder.starts)
        // 녹음 중이 아니면 아무것도 하지 않는다(반복을 시작하지 않는다).
        model.cancelEvaluation()
        assertNull(model.uiState.value.countdown)
    }

    @Test fun abortPausesTheRecordingUntilTheUserChooses() = runTest {
        // 연주 평가처럼 ‘연주 중단’은 녹음·진행을 그 자리에서 멈추고 묻는다. 묻는 동안 녹음이 끝나 분석으로 넘어가면 안 된다.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = FakePracticeRecorder(RecordedAudio("section.m4a", 2048))
        val model = keep(SectionLoopViewModel("30", section, 60, 1, source, recorder, Mic(granted = true), newKey = { "k" }, autoStart = false))
        advanceUntilIdle()
        model.startEvaluation()
        advanceTimeBy(CountdownTickMillis * CountdownBeats + model.uiState.value.cellMillis.toLong() * 2 + 1)
        runCurrent()
        val before = model.uiState.value
        assertTrue(before.recording && before.running && before.step > 0)

        model.askAbort()
        advanceTimeBy(60_000)
        runCurrent()
        val asked = model.uiState.value
        assertTrue(asked.abortAsked)
        assertFalse(asked.running)
        assertEquals(before.step, asked.step)
        assertNull(asked.take)

        // ‘계속 연주’: 3·2·1 뒤 멈춘 자리부터 이어 끝까지 녹음해 넘긴다.
        model.keepRecording()
        assertEquals(CountdownBeats, model.uiState.value.countdown)
        assertFalse(model.uiState.value.abortAsked)
        advanceUntilIdle()
        assertEquals("section.m4a", model.uiState.value.take?.take?.path)
        assertEquals(1, recorder.starts)
    }

    @Test fun waitingAfterAbortDropsThePausedRecording() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = FakePracticeRecorder(RecordedAudio("section.m4a", 2048))
        val model = keep(SectionLoopViewModel("30", section, 60, 1, source, recorder, Mic(granted = true), newKey = { "k" }, autoStart = false))
        advanceUntilIdle()
        model.startEvaluation()
        advanceTimeBy(CountdownTickMillis * CountdownBeats + model.uiState.value.cellMillis.toLong() + 1)
        runCurrent()
        model.askAbort()
        model.cancelEvaluation()
        advanceUntilIdle()
        val state = model.uiState.value
        assertFalse(state.recording)
        assertFalse(state.abortAsked)
        assertNull(state.take)
        assertEquals(1, recorder.cancels)
        // 버린 뒤에는 ‘계속 연주’가 아무것도 하지 않는다.
        model.keepRecording()
        assertNull(model.uiState.value.countdown)
    }

    @Test
    fun recordsSectionOnceAndHandsTakeWithNoteRangeAndParent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = FakePracticeRecorder(RecordedAudio("section.m4a", 2048))
        val model = model(recorder)
        advanceUntilIdle()
        assertFalse(model.uiState.value.loading)

        model.startEvaluation()
        advanceUntilIdle()

        val state = model.uiState.value
        val take = assertNotNull(state.take, "notice=${state.notice} recording=${state.recording} running=${state.running} finished=${state.finished} starts=${recorder.starts}")
        val notes = checkNotNull(noteRange(song, section))
        assertEquals(SessionSection(notes.first, notes.last, "30"), take.section)
        assertEquals("section.m4a" to "take-key", take.take.path to take.take.key)
        assertEquals(1, recorder.starts)
        assertFalse(state.recording)

        model.takeConsumed()
        assertNull(model.uiState.value.take)
    }

    @Test fun sectionTakeCarriesTheRevisionUsedByTheRecorder() = runTest {
        val published = object : WeakSectionRepository by source {
            override suspend fun source(sourceId: String) = WeakSectionSource(song.copy(scoreVersion = 1), checkNotNull(CatalogEvaluations().cached))
        }
        val model = keep(SectionLoopViewModel("30", section, 60, 1, published, FakePracticeRecorder()))
        advanceUntilIdle()
        model.startEvaluation()
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.take?.take?.scoreVersion)
    }

    @Test
    fun noteRangeCountsOnlyPitchNotesInsideSection() {
        val notes = checkNotNull(noteRange(song, section))
        val inside = song.timeline.filter { it.noteIndex != null && section.contains(it.column, it.row) }.map { it.noteIndex!! }
        assertEquals(inside.min()..inside.max(), notes)
    }

    @Test
    fun webWithoutRecorderExplainsInsteadOfRecording() = runTest {
        val model = model(recorder = null)
        advanceUntilIdle()
        model.startEvaluation()
        assertEquals(SectionLoopNotice.NoRecorder, model.uiState.value.notice)
        assertFalse(model.uiState.value.recording)
        model.noticeShown()
        assertNull(model.uiState.value.notice)
    }

    @Test
    fun deniedMicDoesNotRecordAndBlockedMicPointsToSettings() = runTest {
        val recorder = FakePracticeRecorder()
        val denied = model(recorder, Mic(granted = false, allow = false))
        advanceUntilIdle()
        denied.startEvaluation()
        advanceUntilIdle()
        assertEquals(SectionLoopNotice.MicDenied, denied.uiState.value.notice)

        val blocked = model(recorder, Mic(granted = false, allow = false, blocked = true))
        advanceUntilIdle()
        blocked.startEvaluation()
        advanceUntilIdle()
        assertEquals(SectionLoopNotice.MicBlocked, blocked.uiState.value.notice)
        assertEquals(0, recorder.starts)
    }

    @Test
    fun askingMicOnFirstEvaluationThenRecords() = runTest {
        val mic = Mic(granted = false, allow = true)
        val model = model(mic = mic)
        advanceUntilIdle()
        model.startEvaluation()
        advanceUntilIdle()
        assertEquals(1, mic.requests)
        assertTrue(model.uiState.value.take != null)
    }

    @Test
    fun stoppingWhileRecordingDiscardsTake() = runTest {
        val recorder = FakePracticeRecorder()
        val model = model(recorder)
        advanceUntilIdle()
        model.startEvaluation()
        advanceTimeBy(CountdownBeats * CountdownTickMillis + 500)
        runCurrent()
        assertTrue(model.uiState.value.running)

        model.togglePause()
        advanceUntilIdle()
        assertEquals(1, recorder.cancels)
        assertFalse(model.uiState.value.recording)
        assertNull(model.uiState.value.take)
    }

    @Test
    fun unsavedOrTooLargeRecordingIsReported() = runTest {
        val missing = model(FakePracticeRecorder(output = null))
        advanceUntilIdle()
        missing.startEvaluation()
        advanceUntilIdle()
        assertEquals(SectionLoopNotice.RecordFailed, missing.uiState.value.notice)
        assertNull(missing.uiState.value.take)

        val large = model(FakePracticeRecorder(RecordedAudio("big.m4a", RecordingSpec.MAX_BYTES + 1)))
        advanceUntilIdle()
        large.startEvaluation()
        advanceUntilIdle()
        assertEquals(SectionLoopNotice.RecordFailed, large.uiState.value.notice)
    }

    @Test
    fun interruptedRecordingIsDiscarded() = runTest {
        val recorder = FakePracticeRecorder()
        val model = model(recorder)
        advanceUntilIdle()
        model.startEvaluation()
        advanceTimeBy(CountdownBeats * CountdownTickMillis + 10)
        runCurrent()
        recorder.interrupted = true
        advanceUntilIdle()
        assertEquals(SectionLoopNotice.RecordFailed, model.uiState.value.notice)
        assertEquals(1, recorder.cancels)
        assertNull(model.uiState.value.take)
    }

    @Test
    fun recordingStateRunsOnceRegardlessOfRepeats() {
        val cells = section.positions(song.score)
        var state = SectionLoopUiState(song = song, section = section, cells = cells, repeats = 5).beginRecording()
        state = state.countdownTick().countdownTick().countdownTick()
        repeat(cells.size) { state = state.advance() }
        assertTrue(state.finished)
        assertEquals(1, state.round)
        assertFalse(state.endRecording().recording)
    }

    @Test
    fun serverPickBecomesWholeColumnsAndOutOfScoreIsIgnored() {
        assertEquals(ScoreSection.ofColumns(song.score, 1, 2), WeakSectionPick(1, 2, 5).toSection(song.score))
        assertNull(WeakSectionPick(0, song.score.columns.size, 5).toSection(song.score))
        assertNull(WeakSectionPick(3, 2, 5).toSection(song.score))
    }
}

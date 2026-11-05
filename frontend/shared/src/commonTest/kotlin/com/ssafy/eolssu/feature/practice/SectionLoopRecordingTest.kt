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

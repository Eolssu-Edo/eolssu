package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.EvaluationNote
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.notesSection
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PreviousRecord
import com.ssafy.eolssu.data.practice.RecordComparison
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.SessionCheck
import com.ssafy.eolssu.data.practice.UnavailablePracticeRecordRepository
import com.ssafy.eolssu.data.practice.WeakSectionPick
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.data.practice.WeakSectionSource
import com.ssafy.eolssu.data.song.SampleSongs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 구간 재평가 결과의 평가한 정간과 ‘연습 기록 저장’(S15P31E102-832). */
@OptIn(ExperimentalCoroutinesApi::class)
class SectionResultSaveTest {
    /** 서버 곡처럼 율명 음마다 음 번호(noteIndex)를 붙인 아리랑. */
    private val song = SampleSongs.first { it.id == "arirang" }.let { base ->
        var noteIndex = 0
        base.copy(score = base.score.copy(events = base.timeline.map { if (it.note is ScoreNote.Pitch) it.copy(noteIndex = noteIndex++) else it }))
    }

    private fun result(notes: IntRange?) = EvaluationResult(
        id = "section-session", songId = song.id, total = 80, feedback = "", metrics = emptyList(), errors = emptyList(),
        bpm = 52, recordedSeconds = 8, recordedAt = KoreanDateTime(2026, 11, 12, 10, 0), sample = false,
        notes = notes?.map { EvaluationNote(it, "CORRECT") }.orEmpty(),
    )

    private val check = CheckLoad.Loaded(
        SessionCheck(
            comparable = true, reason = null, beforeErrors = 3, afterErrors = 1, deltaErrors = -2, correctedCount = 2,
            startColumn = 1, endColumn = 2,
        ),
    )

    private fun evaluation(notes: IntRange? = 5..11, withResult: Boolean = true) = SectionEvaluation(
        song, "section-session", bpm = 52, total = 80, metrics = emptyList(), check = check,
        result = if (withResult) result(notes) else null,
    )

    @Test
    fun playedSectionCoversExactlyTheEvaluatedNotes() {
        // 서버 비교는 각 단위(1~2각)만 알려 주지만, 하이라이트는 평가한 음 범위 그대로다.
        val notes = 5..11
        val played = assertNotNull(evaluation(notes).playedSection())
        assertEquals(song.notesSection(notes), played)
        val covered = song.timeline.filter { it.noteIndex != null && played.contains(it.column, it.row) }.map { it.noteIndex!! }
        assertEquals(notes.toList(), covered)
        assertNull(evaluation(notes = null).playedSection(), "음 판정이 없으면 평가한 정간을 모른다")
    }

    @Test
    fun saveOpensTheFormThenSavesTheSectionSessionOnceWithTheMemo() = withSection { model, records ->
        assertTrue(model.uiState.value.canSave)
        model.save()
        assertEquals(SaveSheet.Form, model.uiState.value.saveSheet)
        assertEquals(0, records.saveCalls, "‘연습 기록 저장’만 눌러서는 저장하지 않는다")
        model.changeMemo("  2각 박이 밀림 ")
        model.confirmSave()
        runCurrent()
        assertEquals(listOf("section-session"), records.savedIds, "구간만 녹음한 세션을 저장한다")
        assertEquals("아리랑", records.lastTitle)
        assertEquals("2각 박이 밀림", records.lastMemo)
        assertEquals(SaveSheet.Saved, model.uiState.value.saveSheet)
        assertNotNull(model.uiState.value.savedRecord)
        // 저장 뒤 다시 누르면 ②만 뜨고 다시 저장하지 않는다.
        model.closeSheet()
        model.save()
        assertEquals(SaveSheet.Saved, model.uiState.value.saveSheet)
        model.confirmSave()
        runCurrent()
        assertEquals(1, records.saveCalls)
    }

    @Test
    fun repeatedTapsWhileSavingWriteOnceAndKeepTheSheet() {
        val records = SavedRecords().apply { gate = CompletableDeferred() }
        withSection(records) { model, _ ->
            model.save()
            model.confirmSave()
            model.confirmSave()
            runCurrent()
            assertEquals(1, records.saveCalls)
            assertTrue(model.uiState.value.savingRecord)
            model.closeSheet()
            assertEquals(SaveSheet.Form, model.uiState.value.saveSheet, "저장 중에는 시트를 닫지 않는다")
            records.gate?.complete(Unit)
            runCurrent()
            assertEquals(SaveSheet.Saved, model.uiState.value.saveSheet)
            assertFalse(model.uiState.value.savingRecord)
        }
    }

    @Test
    fun failedSaveKeepsTheFormAndMemo() = withSection(UnavailablePracticeRecordRepository()) { model, _ ->
        model.save()
        model.changeMemo("초안")
        model.confirmSave()
        runCurrent()
        assertEquals(RecordSaveError.Unavailable, model.uiState.value.recordSaveError)
        assertNull(model.uiState.value.savedRecord)
        assertEquals(SaveSheet.Form, model.uiState.value.saveSheet)
        assertEquals("초안", model.uiState.value.memo)
        assertFalse(model.uiState.value.savingRecord)
        model.dismissRecordSaveError()
        assertNull(model.uiState.value.recordSaveError)
    }

    @Test
    fun cannotSaveWithoutTheEvaluationResult() {
        val records = SavedRecords()
        withSection(records, evaluation(withResult = false)) { model, _ ->
            assertFalse(model.uiState.value.canSave)
            model.save()
            model.confirmSave()
            runCurrent()
            assertNull(model.uiState.value.saveSheet)
            assertEquals(0, records.saveCalls)
        }
    }

    private fun withSection(
        records: PracticeRecordRepository = SavedRecords(),
        evaluation: SectionEvaluation = evaluation(),
        block: suspend TestScope.(SectionResultViewModel, SavedRecords) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val model = SectionResultViewModel("section-session", OneSection(evaluation), recordRepository = records)
            store.put("section-result", model)
            runCurrent()
            assertNotNull(model.uiState.value.evaluation)
            block(model, records as? SavedRecords ?: SavedRecords())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private class OneSection(private val evaluation: SectionEvaluation) : WeakSectionRepository {
        override suspend fun source(sourceId: String): WeakSectionSource? = error("unused")
        override suspend fun weakSections(sessionId: String): List<WeakSectionPick>? = error("unused")
        override suspend fun sectionResult(sessionId: String): SectionEvaluation? = evaluation.takeIf { it.sessionId == sessionId }
        override suspend fun comparison(recordId: String): RecordComparison? = error("unused")
        override suspend fun previous(currentId: String, previousId: String): PreviousRecord? = error("unused")
    }

    private class SavedRecords : PracticeRecordRepository {
        override val records = MutableStateFlow(emptyList<PracticeRecord>())
        val savedIds = mutableListOf<String>()
        val saveCalls get() = savedIds.size
        var lastTitle: String? = null
        var lastMemo: String? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String): PracticeRecord {
            savedIds += result.id
            lastTitle = songTitle
            lastMemo = memo
            gate?.await()
            return PracticeRecord(
                id = result.id, songId = result.songId, songTitle = songTitle,
                section = "2–3각", repeats = null, recordedAt = result.recordedAt,
                recordedSeconds = result.recordedSeconds, bpm = result.bpm, score = result.total,
                metrics = result.metrics, memo = memo, errors = result.errors,
            )
        }

        override suspend fun updateMemo(recordId: String, memo: String) = Unit
    }
}

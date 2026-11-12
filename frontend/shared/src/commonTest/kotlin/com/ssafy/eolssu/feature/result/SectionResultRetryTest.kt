package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.EvaluationNote
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.notesSection
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.SessionCheck
import com.ssafy.eolssu.data.song.SampleSongs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 구간 재평가 결과의 ‘구간 재평가 다시하기’가 다시 열 구간(S15P31E102-826). */
class SectionResultRetryTest {
    /** 서버 곡처럼 율명 음마다 음 번호(noteIndex)를 붙인 아리랑. */
    private val song = SampleSongs.first { it.id == "arirang" }.let { base ->
        var noteIndex = 0
        base.copy(score = base.score.copy(events = base.timeline.map { if (it.note is ScoreNote.Pitch) it.copy(noteIndex = noteIndex++) else it }))
    }

    private fun result(notes: IntRange?) = EvaluationResult(
        id = "session", songId = song.id, total = 80, feedback = "", metrics = emptyList(), errors = emptyList(),
        bpm = 52, recordedSeconds = 8, recordedAt = KoreanDateTime(2026, 11, 12, 10, 0), sample = false,
        notes = notes?.map { EvaluationNote(it, "CORRECT") }.orEmpty(),
    )

    private fun check(startColumn: Int?, endColumn: Int?) = CheckLoad.Loaded(
        SessionCheck(
            comparable = true, reason = null, beforeErrors = 3, afterErrors = 1, deltaErrors = -2, correctedCount = 2,
            startColumn = startColumn, endColumn = endColumn,
        ),
    )

    private fun state(notes: IntRange?, check: CheckLoad) = SectionResultUiState(
        loading = false,
        evaluation = SectionEvaluation(song, "session", bpm = 52, total = 80, metrics = emptyList(), check = check, result = result(notes)),
    )

    @Test
    fun retriesTheNotesThatWereEvaluated() {
        // 각 중간에서 시작하고 끝난 구간: 서버 비교는 각 단위(1~2각)만 알려 주지만, 다시 열 구간은 평가한 음 범위 그대로다.
        val notes = 5..11
        val played = assertNotNull(song.notesSection(notes))
        val state = state(notes, check(played.start.column, played.end.column))
        assertEquals(played, state.retrySection)
        // 다시 연 구간이 담는 음 번호가 평가한 세션의 음 범위와 같다.
        val retried = song.timeline.filter { it.noteIndex != null && state.retrySection!!.contains(it.column, it.row) }.map { it.noteIndex!! }
        assertEquals(notes.toList(), retried)
    }

    @Test
    fun fallsBackToTheComparedColumnsWithoutNoteVerdicts() {
        // 음 판정을 받지 못했으면 서버 비교가 알려 준 각 전체를 다시 연다.
        val state = state(notes = null, check = check(startColumn = 1, endColumn = 2))
        assertEquals(ScoreSection.ofColumns(song.score, 1, 2), state.retrySection)
    }

    @Test
    fun hasNoSectionWhenNeitherIsKnown() {
        assertNull(state(notes = null, check = CheckLoad.Failed).retrySection)
        assertNull(SectionResultUiState().retrySection)
    }
}

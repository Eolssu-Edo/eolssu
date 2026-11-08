package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.score.errorCount
import com.ssafy.eolssu.core.ui.score.visibleErrors
import com.ssafy.eolssu.core.ui.score.visibleErrorKinds
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors

import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreErrorKind
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultErrorViewTest {

    private fun result(errors: List<ScoreError>) = EvaluationResult(
        id = "e", songId = "s", total = 80, feedback = "", metrics = emptyList(), errors = errors,
        bpm = 60, recordedSeconds = 10, recordedAt = KoreanDateTime(2026, 10, 20, 15, 20), sample = true,
    )

    private val errors = listOf(
        ScoreError(0, 4, EvaluationMetric.Rhythm),
        ScoreError(3, 1, EvaluationMetric.Pitch),
        ScoreError(2, 5, EvaluationMetric.Length),
        ScoreError(2, 2, EvaluationMetric.Pitch),
    )

    @Test
    fun filterCountsAndShowsOnlyMatchingErrors() {
        val r = result(errors)
        assertEquals(4, r.errorCount(ErrorFilter.All))
        assertEquals(2, r.errorCount(ErrorFilter.Pitch))
        assertEquals(1, r.errorCount(ErrorFilter.Rhythm))
        assertEquals(1, r.errorCount(ErrorFilter.Length))
        assertEquals(mapOf((3 to 1) to ScoreErrorKind.Pitch, (2 to 2) to ScoreErrorKind.Pitch), r.visibleErrors(ErrorFilter.Pitch))
        assertEquals(4, r.visibleErrors(ErrorFilter.All).size)
    }

    @Test
    fun errorAtIgnoresFilter() {
        val r = result(errors)
        assertEquals(ScoreErrorKind.Length, r.errorAt(ScoreCellPosition(2, 5)))
        assertNull(r.errorAt(ScoreCellPosition(1, 1)))
    }

    @Test
    fun defaultSelectionIsFirstErrorOfDensestPair() {
        // 2–3각(0부터)에 3개가 몰려 있고, 그중 연주 순서로 첫 오류는 2각 2정간이다.
        assertEquals(ScoreCellPosition(2, 2), result(errors).defaultSelectedCell())
    }

    @Test
    fun defaultSelectionWithoutErrorsIsFirstCell() {
        assertEquals(ScoreCellPosition(0, 0), result(emptyList()).defaultSelectedCell())
    }

    @Test
    fun cellWithSeveralErrorKindsKeepsEveryKind() {
        // 한 정간에 음이 둘이라 박자·음정 오류가 함께 났다. 연주 순서와 관계없이 종류 순서로 모두 남긴다.
        val r = result(
            listOf(
                ScoreError(1, 2, ScoreErrorKind.Missed),
                ScoreError(1, 2, ScoreErrorKind.Rhythm),
                ScoreError(1, 2, ScoreErrorKind.Pitch),
                ScoreError(0, 0, ScoreErrorKind.Rhythm),
            ),
        )

        assertEquals(listOf(ScoreErrorKind.Pitch, ScoreErrorKind.Rhythm, ScoreErrorKind.Missed), r.errorKindsAt(ScoreCellPosition(1, 2)))
        assertEquals(ScoreErrorKind.Pitch, r.errorAt(ScoreCellPosition(1, 2)))
        assertEquals(
            mapOf((1 to 2) to listOf(ScoreErrorKind.Pitch, ScoreErrorKind.Rhythm, ScoreErrorKind.Missed), (0 to 0) to listOf(ScoreErrorKind.Rhythm)),
            r.visibleErrorKinds(ErrorFilter.All),
        )
        assertEquals(mapOf((1 to 2) to listOf(ScoreErrorKind.Rhythm), (0 to 0) to listOf(ScoreErrorKind.Rhythm)), r.visibleErrorKinds(ErrorFilter.Rhythm))
        assertEquals(mapOf((1 to 2) to ScoreErrorKind.Pitch, (0 to 0) to ScoreErrorKind.Rhythm), r.visibleErrors(ErrorFilter.All))
        // 전체 수는 틀린 정간 수, 종류별 수는 그 종류가 있는 정간 수다.
        assertEquals(2, r.errorCount(ErrorFilter.All))
        assertEquals(2, r.errorCount(ErrorFilter.Rhythm))
        assertEquals(1, r.errorCount(ErrorFilter.Pitch))
        assertEquals(1, r.errorCount(ErrorFilter.Missed))
    }

    private fun unit(index: Int, unitRow: Int) = ScoreDisplayUnit(
        column = 1, row = 2, unitRow = unitRow, unitCol = 0, symbol = "黃", note = ScoreNote.Rest,
        event = ScoreEvent(index, "e$index", 1, 2, unitRow, 2, ScoreNote.Rest, 3.0 + unitRow / 2.0, 0.5),
        unitStart = 3.0 + unitRow / 2.0, unitDuration = 0.5,
    )

    @Test
    fun splitCellShowsEachSubBeatsOwnVerdict() {
        val first = unit(index = 7, unitRow = 0)
        val second = unit(index = 8, unitRow = 1)
        val r = result(listOf(ScoreError(1, 2, ScoreErrorKind.Rhythm))).copy(eventErrors = mapOf(8 to listOf(ScoreErrorKind.Rhythm)))

        assertEquals(emptyList(), r.errorKindsAt(first))
        assertEquals(listOf(ScoreErrorKind.Rhythm), r.errorKindsAt(second))
        // 소박을 누르지 않았으면 처음 틀린 소박을, 눌렀으면 그 소박을 보여준다.
        assertEquals(second, r.shownUnit(ScoreCellPosition(1, 2), listOf(first, second)))
        assertEquals(first, r.shownUnit(ScoreCellPosition(1, 2, first.key), listOf(first, second)))
    }

    @Test
    fun boardMarksEachSubBeatWithItsOwnVerdict() {
        // 세 소박으로 나뉜 정간: 첫째·둘째는 박자, 셋째는 음정. 정간 전체를 음정으로 칠하지 않는다.
        val note = ScoreNote.Pitch(com.ssafy.eolssu.core.model.Yul.Hwangjong)
        val song = com.ssafy.eolssu.data.song.smallScoreSong().copy(
            score = com.ssafy.eolssu.core.model.Score(listOf(listOf(com.ssafy.eolssu.core.model.ScoreCell(note, note, note)))),
        )
        val units = song.displayUnits
        val r = result(listOf(ScoreError(0, 0, ScoreErrorKind.Pitch), ScoreError(0, 0, ScoreErrorKind.Rhythm))).copy(
            eventErrors = mapOf(
                units[0].event.index to listOf(ScoreErrorKind.Rhythm),
                units[1].event.index to listOf(ScoreErrorKind.Rhythm),
                units[2].event.index to listOf(ScoreErrorKind.Pitch),
            ),
        )

        val all = r.visibleUnitErrors(song, ErrorFilter.All)
        assertEquals(listOf(ScoreErrorKind.Rhythm), all[units[0].key])
        assertEquals(listOf(ScoreErrorKind.Rhythm), all[units[1].key])
        assertEquals(listOf(ScoreErrorKind.Pitch), all[units[2].key])
        assertEquals(setOf(units[2].key), r.visibleUnitErrors(song, ErrorFilter.Pitch).keys)
        // 음별 판정이 없는 예시 결과는 정간 단위로 칠한다.
        assertEquals(emptyMap(), r.copy(eventErrors = emptyMap()).visibleUnitErrors(song, ErrorFilter.All))
    }

    @Test
    fun sampleWithoutPerNoteVerdictsFallsBackToCellKinds() {
        val r = result(listOf(ScoreError(1, 2, ScoreErrorKind.Pitch)))
        assertEquals(listOf(ScoreErrorKind.Pitch), r.errorKindsAt(unit(index = 7, unitRow = 0)))
    }

    @Test
    fun missedNoteRemainsAnErrorWithoutBecomingLengthError() {
        val r = result(listOf(ScoreError(2, 3, ScoreErrorKind.Missed))).copy(sample = false)

        assertEquals(1, r.errorCount(ErrorFilter.All))
        assertEquals(1, r.errorCount(ErrorFilter.Missed))
        assertEquals(0, r.errorCount(ErrorFilter.Length))
        assertEquals(mapOf((2 to 3) to ScoreErrorKind.Missed), r.visibleErrors(ErrorFilter.All))
        assertEquals(mapOf((2 to 3) to ScoreErrorKind.Missed), r.visibleErrors(ErrorFilter.Missed))
        assertEquals(emptyMap(), r.visibleErrors(ErrorFilter.Length))
        assertEquals(ScoreErrorKind.Missed, r.errorAt(ScoreCellPosition(2, 3)))
        assertEquals(ScoreCellPosition(2, 3), r.defaultSelectedCell())
    }
}

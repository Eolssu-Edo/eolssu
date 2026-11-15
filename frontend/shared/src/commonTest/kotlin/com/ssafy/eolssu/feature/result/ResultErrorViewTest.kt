package com.ssafy.eolssu.feature.result

import androidx.compose.ui.graphics.Color
import com.ssafy.eolssu.core.ui.score.ScoreCellDecoration
import com.ssafy.eolssu.core.ui.score.errorCount
import com.ssafy.eolssu.core.ui.score.wholeCellErrors
import com.ssafy.eolssu.core.ui.score.visibleErrors
import com.ssafy.eolssu.core.ui.score.visibleErrorKinds
import com.ssafy.eolssu.core.ui.score.visibleUnitErrors

import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationNote
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.data.song.smallScoreSong
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
        // 음별 판정이 없는 예시 결과는 정간 단위로 칠하므로 전체 수는 틀린 정간 수, 종류별 수는 그 종류가 있는 정간 수다.
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

    /**
     * 2소박 정간(음 둘) · 3소박 정간(음 셋) · 나뉘지 않은 정간 둘. 한 정간은 6 tick이다.
     * c0-j0 = 0·1번 음, c0-j1 = 2·3·4번 음, c0-j2 = 5번 음, c1-j0 = 6번 음.
     */
    private fun subBeatSong(): Song {
        val small = smallScoreData()
        fun note(cell: String, row: Int, col: Int, start: Long, length: Long) =
            CommonScoreData.Event("$cell-r$row-u$col", "NOTE", "황", 0, start, length, cell, false, emptyList())
        val events = listOf(
            note("c0-j0", 0, 0, 0, 3), note("c0-j0", 0, 1, 3, 3),
            note("c0-j1", 0, 0, 6, 2), note("c0-j1", 0, 1, 8, 2), note("c0-j1", 0, 2, 10, 2),
            note("c0-j2", 0, 0, 12, 6),
            note("c1-j0", 0, 0, 18, 6),
        )
        fun cell(column: Int, row: Int, count: Int) = CommonScoreData.Cell(
            "c$column-j$row", row, (0 until count).map { CommonScoreData.Unit(0, it, "황", "c$column-j$row-r0-u$it") },
        )
        return smallScoreSong(
            small.copy(
                music = small.music.copy(ticksPerBeat = 3, ticksPerJeonggan = 6),
                columns = listOf(
                    CommonScoreData.Column(0, listOf(cell(0, 0, 2), cell(0, 1, 3), cell(0, 2, 1))),
                    CommonScoreData.Column(1, listOf(cell(1, 0, 1))),
                ),
                events = events,
            ),
        )
    }

    private fun scored(song: Song, vararg verdicts: String) = result(emptyList())
        .copy(songId = song.id, sample = false, notes = verdicts.mapIndexed { i, v -> EvaluationNote(i, v) })
        .withScore(song)

    /**
     * 정간보에 칠해지는 틀린 음([ScoreEvent.index]). [JeongganboView]와 같이 소박으로 나뉜 정간은 틀린 소박만,
     * 나뉘지 않은 정간은 정간 전체를 칠한다. 한 음이 이음으로 여러 자리를 칠해도 한 음으로 본다.
     */
    private fun markedNotes(r: EvaluationResult, song: Song, filter: ErrorFilter): Set<Int> {
        val unitErrors = r.visibleUnitErrors(song, filter)
        val cellKinds = r.visibleErrorKinds(filter)
        return song.displayUnits.groupBy { it.column to it.row }.flatMap { (cell, units) ->
            when {
                unitErrors.isNotEmpty() && units.size > 1 -> units.filter { it.key in unitErrors }.map { it.event.index }
                cell in cellKinds -> units.map { it.event.index }
                else -> emptyList()
            }
        }.toSet()
    }

    @Test
    fun subBeatErrorsAreCountedPerWrongNoteLikeTheMarks() {
        val song = subBeatSong()
        // 2소박 정간은 두 음 모두 음정, 3소박 정간은 박자·음정·미검출, 나뉘지 않은 정간은 맞음·음정.
        val r = scored(song, "PITCH", "PITCH", "TIMING", "PITCH", "MISSED", "CORRECT", "PITCH")

        assertEquals(6, r.errorCount(ErrorFilter.All))
        assertEquals(4, r.errorCount(ErrorFilter.Pitch))
        assertEquals(1, r.errorCount(ErrorFilter.Rhythm))
        assertEquals(1, r.errorCount(ErrorFilter.Missed))
        assertEquals(0, r.errorCount(ErrorFilter.Length))
        // 정간 단위였다면 전체 3·음정 3으로 줄어든다.
        assertEquals(3, r.errors.distinctBy { it.column to it.row }.size)
        ErrorFilter.entries.forEach { filter ->
            assertEquals(markedNotes(r, song, filter).size, r.errorCount(filter), "$filter 개수와 칠한 표시 수")
        }
    }

    @Test
    fun heldNoteMarkedOverSeveralSlotsCountsOnce() {
        // 작은 악보의 둘째 음은 나뉜 정간에서 '청중'과 이음 '-' 두 자리를 칠하지만 한 음이다.
        val song = smallScoreSong()
        val r = scored(song, "CORRECT", "PITCH")

        val painted = r.visibleUnitErrors(song, ErrorFilter.Pitch).keys
        assertEquals(2, song.displayUnits.count { it.column == 0 && it.row == 0 && it.key in painted })
        assertEquals(1, r.errorCount(ErrorFilter.Pitch))
        assertEquals(1, r.errorCount(ErrorFilter.All))
        assertEquals(markedNotes(r, song, ErrorFilter.All).size, r.errorCount(ErrorFilter.All))
    }

    @Test
    fun sectionSelectPaintsSplitCellsPerSubBeatLikeTheCount() {
        // 구간 고르기는 정간 꾸밈으로 칠한다. 나뉜 정간(c0-j0·c0-j1)은 꾸밈에서 빼고 틀린 소박만 칠한다.
        val song = subBeatSong()
        val r = scored(song, "PITCH", "PITCH", "TIMING", "PITCH", "MISSED", "CORRECT", "PITCH")
        val split = setOf(0 to 0, 0 to 1)

        assertEquals(listOf(ScoreError(1, 0, ScoreErrorKind.Pitch)), r.wholeCellErrors(song, ErrorFilter.All))
        assertEquals(emptyList(), r.wholeCellErrors(song, ErrorFilter.Rhythm))
        ErrorFilter.entries.forEach { filter ->
            val wholeCells = r.wholeCellErrors(song, filter).map { it.column to it.row }.toSet()
            val unitErrors = r.visibleUnitErrors(song, filter)
            val painted = song.displayUnits.filter {
                (it.column to it.row) in wholeCells || ((it.column to it.row) in split && it.key in unitErrors)
            }.map { it.event.index }.toSet()
            assertEquals(r.errorCount(filter), painted.size, "$filter 개수와 구간 고르기에서 칠한 수")
        }
        // 음별 판정이 없는 예시 결과는 나뉜 정간도 정간 전체로 칠한다.
        val sample = result(listOf(ScoreError(0, 0, ScoreErrorKind.Pitch)))
        assertEquals(sample.errors, sample.wholeCellErrors(song, ErrorFilter.All))
    }

    @Test
    fun onlyCellPaintingDecorationsHideSubBeatMarks() {
        // 구간 밖 흐리기·처음/끝 테두리·알약만 있는 꾸밈은 틀린 소박 표시와 함께 그린다.
        assertEquals(false, ScoreCellDecoration(alpha = 0.35f).paintsCell)
        assertEquals(false, ScoreCellDecoration(ring = Color.Black, edgeLabel = "처음").paintsCell)
        assertEquals(true, ScoreCellDecoration(fill = Color.Red).paintsCell)
        assertEquals(true, ScoreCellDecoration(tag = ScoreErrorKind.Pitch).paintsCell)
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

package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LivePitchMarkTest {
    /** 작은 검사 악보: 황(311.13Hz) · 꾸밈음 있는 청중 · 쉼. */
    private val song = smallScoreSong()

    /** 꾸밈음을 지우고 두 번째 음(청중)을 [yul]·[octave]·악보 글자 [symbol]로 바꾼 악보. 두 번째 음은 두 정간에 걸친다. */
    private fun plain(yul: String = "중", octave: Int = 1, symbol: String = "청중"): Song {
        val data = smallScoreData()
        return smallScoreSong(data.copy(
            columns = data.columns.map { column ->
                column.copy(cells = column.cells.map { cell ->
                    cell.copy(units = cell.units.map { if (it.symbol == "청중") it.copy(symbol = symbol) else it })
                })
            },
            events = data.events.mapIndexed { i, e ->
                if (i == 1) e.copy(yul = yul, octave = octave, sigimsae = emptyList()) else e.copy(sigimsae = emptyList())
            },
        ))
    }

    private val recording = PracticeUiState(song = song, mode = PracticeMode.Recording)
    private var clock = 0L

    /** [index]번 음의 목표보다 [cents]센트 벗어난 확정 값 하나. [held]면 측정 오류로 보류된 값이다. */
    private fun window(song: Song, index: Int, cents: Double, held: Boolean = false): PracticePitchFeedback {
        val target = targetHz(song.timeline[index].note, 311.13)!!
        val value = PitchFeedback(song.timeline[index].id, "", target * 2.0.pow(cents / 1200), if (held) null else true, ++clock)
        return PracticePitchFeedback(current = value)
    }

    private fun tally(vararg values: PracticePitchFeedback) = values.fold(LivePitchTally()) { acc, value -> acc.add(value) }

    private fun LivePitchTally.byIndex(song: Song) = marks(song).mapKeys { (id, _) -> song.timeline.indexOfFirst { it.id == id } }

    private fun keysOf(song: Song, index: Int) =
        song.displayUnits.filter { it.event.id == song.timeline[index].id }.map { it.key }.toSet()

    @Test fun usesTheMiddleSixtyPercentOfEachNoteLikeTheServer() {
        // 앞뒤 20%(소리 시작·끝의 흔들림)는 버린다. 앞의 +400, 뒤의 −400을 넣어도 맞음이다.
        val song = plain()
        val counted = tally(window(song, 0, 400.0), window(song, 0, 0.0), window(song, 0, 5.0), window(song, 0, -5.0), window(song, 0, -400.0))
        assertEquals(mapOf(0 to LivePitchMark.Correct), counted.byIndex(song))
    }

    @Test fun tuningOffsetIsTheMedianOfPlausibleNoteErrorsWithinTheServerCap() {
        assertEquals(0.0, livePitchTuningOffset(listOf(0.0, 0.0, 70.0), 100.0))
        assertEquals(-80.0, livePitchTuningOffset(listOf(-85.0, -80.0, -75.0), 100.0))
        // 150센트 넘게 벗어난 음(다른 음을 불었거나 앞뒤 음 소리가 섞임)은 추정에서 뺀다.
        assertEquals(10.0, livePitchTuningOffset(listOf(10.0, 10.0, 600.0, -300.0), 100.0))
        // 상한을 넘으면 서버처럼 보정하지 않는다.
        assertEquals(0.0, livePitchTuningOffset(listOf(-80.0, -80.0), 40.0))
        assertEquals(0.0, livePitchTuningOffset(listOf(120.0, 130.0, 140.0), 100.0))
        assertEquals(0.0, livePitchTuningOffset(emptyList(), 100.0))
    }

    @Test fun judgesEachNoteAgainstFiftyCentsAfterTheTuningOffset() {
        val song = plain()
        // 두 번째 음이 170센트 벗어나면 조율 추정에서 빠지고, 첫 음 기준(0)으로 보아 다른 음이다.
        assertEquals(mapOf(0 to LivePitchMark.Correct, 1 to LivePitchMark.Wrong),
            tally(window(song, 0, 0.0), window(song, 1, 170.0)).byIndex(song))
        assertEquals(mapOf(0 to LivePitchMark.Correct, 1 to LivePitchMark.Correct),
            tally(window(song, 0, 0.0), window(song, 1, 45.0)).byIndex(song))
        // 한 음뿐이면 그 음의 오차가 곧 조율 차이다. 상한(100) 안이면 맞음, 넘으면 다른 음이다.
        assertEquals(mapOf(0 to LivePitchMark.Correct), tally(window(song, 0, -80.0)).byIndex(song))
        assertEquals(mapOf(0 to LivePitchMark.Wrong), tally(window(song, 0, 120.0)).byIndex(song))
    }

    @Test fun capsTheTuningCorrectionLikeTheServerWhenTheSongHasCloseNotes() {
        // 황·대처럼 반음 이웃이 있으면 상한은 100 − 50 − 10 = 40센트다. 80센트 낮은 연주는 보정하지 않아 다른 음이다.
        val close = plain(yul = "대", octave = 0, symbol = "대")
        assertEquals(40.0, livePitchMaxTuningCents(close.timeline, 311.13), 0.001)
        assertEquals(100.0, livePitchMaxTuningCents(plain().timeline, 311.13), 0.001)
        assertEquals(mapOf(0 to LivePitchMark.Wrong, 1 to LivePitchMark.Wrong),
            tally(window(close, 0, -80.0), window(close, 1, -80.0)).byIndex(close))
        assertEquals(mapOf(0 to LivePitchMark.Correct, 1 to LivePitchMark.Correct),
            tally(window(close, 0, -30.0), window(close, 1, -30.0)).byIndex(close))
    }

    @Test fun passedNotesKeepTheirColourWhenLaterNotesMoveTheTuningEstimate() {
        val song = plain()
        val first = tally(window(song, 0, 0.0))
        val later = window(song, 1, 140.0)
        // 고정하지 않으면 두 번째 음(+140)이 조율 추정을 70으로 끌어 첫 음까지 다른 음으로 바뀐다.
        assertEquals(LivePitchMark.Wrong, first.add(later).byIndex(song)[0])
        val frozen = first.freezeBefore(song, 1)
        // 첫 음은 고정한 맞음 그대로이고, 두 번째 음은 조율 추정(70)을 빼도 70센트 벗어나 다른 음이다.
        assertEquals(mapOf(0 to LivePitchMark.Correct, 1 to LivePitchMark.Wrong), frozen.add(later).byIndex(song))
        // 고정한 음에 늦게 도착한 값은 넣지 않는다.
        assertEquals(frozen, frozen.add(window(song, 0, 400.0)))
        // 아직 지나지 않은 음과 값이 없는 음은 고정하지 않는다.
        assertEquals(frozen, frozen.freezeBefore(song, 1))
        assertEquals(LivePitchTally(), LivePitchTally().freezeBefore(song, 3))
    }

    @Test fun showsListeningOnlyWhenEveryValueOfTheNoteWasHeld() {
        val song = plain()
        assertEquals(mapOf(0 to LivePitchMark.Listening), tally(window(song, 0, 0.0, held = true)).byIndex(song))
        assertEquals(mapOf(0 to LivePitchMark.Correct), tally(window(song, 0, 0.0, held = true), window(song, 0, 0.0)).byIndex(song))
    }

    @Test fun addsEachInputWindowOnceEvenWhenReclassifiedOrRepublished() {
        val first = window(song, 0, 0.0)
        val once = tally(first)
        assertEquals(once, once.add(first))
        assertEquals(once, once.add(PracticePitchFeedback(previous = first.current)))
        assertEquals(once, once.add(PracticePitchFeedback(current = first.current!!.copy(heardHz = null, rest = true))))
    }

    @Test fun keepsPassedNotesColouredOnEveryCellOfTheNote() {
        val song = plain()
        // 두 번째 음(+200)은 조율 추정에서 빠지고 다른 음이다. 두 정간에 걸친 표시 자리를 모두 칠한다.
        val counted = tally(window(song, 0, 0.0), window(song, 1, 200.0))
        val marks = livePitchMarks(recording.copy(song = song, position = 2), counted)
        assertTrue(keysOf(song, 1).size > 1)
        assertEquals(keysOf(song, 0).associateWith { LivePitchMark.Correct } + keysOf(song, 1).associateWith { LivePitchMark.Wrong }, marks)
        assertEquals(marks, livePitchMarks(recording.copy(song = song, mode = PracticeMode.Interrupted), counted))
    }

    @Test fun leavesRestOrnamentUnconfirmedPitchAndSoloPracticeEmpty() {
        val silent = window(song, 0, 0.0).current!!.copy(heardHz = null)
        assertEquals(emptyMap(), tally(PracticePitchFeedback(current = silent)).marks(song))
        // 작은 악보의 두 번째 음은 꾸밈음(니레)이 있어 서버처럼 음정을 판정하지 않는다.
        assertEquals(emptyMap(), livePitchMarks(recording, tally(window(song, 1, 300.0))))
        assertEquals(emptyMap(), livePitchMarks(recording.copy(solo = true), tally(window(song, 0, 0.0))))
        assertEquals(emptyMap(), livePitchMarks(recording.copy(song = null), tally(window(song, 0, 0.0))))
    }
}

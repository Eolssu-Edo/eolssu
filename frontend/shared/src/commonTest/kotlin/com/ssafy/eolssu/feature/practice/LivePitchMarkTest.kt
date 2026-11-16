package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.data.practice.PracticeSettings
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

    @Test fun confirmedNotesKeepTheirColourWhenLaterNotesMoveTheTuningEstimate() {
        val song = plain()
        val first = tally(window(song, 0, 0.0))
        val later = window(song, 1, 140.0)
        // 확정하지 않으면 두 번째 음(+140)이 조율 추정을 70으로 끌어 첫 음까지 다른 음으로 바뀐다.
        assertEquals(LivePitchMark.Wrong, first.add(later).byIndex(song)[0])
        val confirmed = first.confirmBefore(song, 1)
        // 첫 음은 확정한 맞음 그대로이고, 두 번째 음은 조율 추정(70)을 빼도 70센트 벗어나 다른 음이다.
        assertEquals(mapOf(0 to LivePitchMark.Correct, 1 to LivePitchMark.Wrong), confirmed.add(later).byIndex(song))
        // 확정한 음에 늦게 도착한 값은 넣지 않는다.
        assertEquals(confirmed, confirmed.add(window(song, 0, 400.0)))
        // 이미 확정한 음은 다시 확정하지 않고, 값이 없는 음은 확정해도 칠하지 않는다.
        assertEquals(confirmed, confirmed.confirmBefore(song, 1))
        assertEquals(emptyMap(), LivePitchTally().confirmBefore(song, 3).shownMarks())
    }

    // 작은 악보는 4틱이 한 박이고 60bpm이라 1틱이 250ms다. 첫 음 0~250ms, 두 번째 음 250~2,000ms(두 정간), 쉼 2,000~3,000ms.
    @Test fun confirmsANoteOnlyAfterItsEndPlusTheLateWindowOrTwoNotesLater() {
        val song = plain()
        val heard = tally(window(song, 0, 0.0), window(song, 1, 0.0))
        fun LivePitchTally.shown() = shownMarks().mapKeys { (id, _) -> song.timeline.indexOfFirst { it.id == id } }
        // 첫 음은 끝(250ms)에서 300ms가 지나기 전에는 확정하지 않는다. 그 사이 온 값은 그대로 더한다.
        assertEquals(emptyMap(), heard.confirm(song, 1, 549.0, 60).shown())
        val atEnd = heard.confirm(song, 1, 550.0, 60)
        assertEquals(mapOf(0 to LivePitchTally.Confirmed(LivePitchMark.Correct, 1)), atEnd.shown())
        // 두 음 뒤로 넘어가면 300ms 전이라도 확정한다(빠른 곡). 직전 음(두 번째 음)은 아직 늦은 값을 받는다.
        assertEquals(setOf(0), heard.confirm(song, 2, 300.0, 60).shown().keys)
        // 지금 부는 음은 시간이 지나도 확정하지 않는다(늘인 음·멈춘 시계).
        assertEquals(setOf(0), heard.confirm(song, 1, 10_000.0, 60).shown().keys)
        // 두 번째 음은 끝(2,000ms) + 300ms에 확정한다.
        assertEquals(setOf(0), atEnd.confirm(song, 2, 2_299.0, 60).shown().keys)
        assertEquals(setOf(0, 1), atEnd.confirm(song, 2, 2_300.0, 60).shown().keys)
        // 박자가 없으면 시간으로 확정하지 않는다.
        assertEquals(heard, heard.confirm(song, 1, 10_000.0, 0))
    }

    @Test fun lateValuesNeverChangeOrRevealAConfirmedNote() {
        val song = plain()
        val confirmed = tally(window(song, 0, 0.0)).confirm(song, 1, 550.0, 60)
        val shown = confirmed.shownMarks()
        // 확정한 뒤 늦게 온 값(다른 음 높이)은 색을 바꾸지 않는다.
        assertEquals(shown, confirmed.add(window(song, 0, 300.0)).add(window(song, 0, 300.0)).shownMarks())
        // 값이 오기 전에 확정한 음은 늦은 값이 와도 칸이 지난 뒤 새로 나타나지 않는다.
        val silent = LivePitchTally().confirm(song, 1, 550.0, 60)
        assertEquals(emptyMap(), silent.add(window(song, 0, 0.0)).shownMarks())
    }

    @Test fun tiedNotePaintsEveryCellTogetherOnlyOnceConfirmed() {
        val song = plain()
        assertTrue(keysOf(song, 1).size > 1)
        val heard = tally(window(song, 0, 0.0), window(song, 1, 0.0))
        // 두 번째 음은 두 정간에 걸친다. 첫 정간을 지나도 음이 끝나지 않았으면 어느 정간도 칠하지 않는다.
        val during = heard.confirm(song, 1, 1_200.0, 60)
        assertEquals(keysOf(song, 0), livePitchMarks(recording.copy(song = song, position = 1), during).keys)
        // 음이 끝나고 확정되면 두 정간을 한꺼번에 칠한다.
        val after = during.confirm(song, 2, 2_300.0, 60)
        assertEquals(keysOf(song, 0) + keysOf(song, 1), livePitchMarks(recording.copy(song = song, position = 2), after).keys)
    }

    @Test fun correctNotesSettleOneNoteAfterTheyAreConfirmedOrWhenTheTakeEnds() {
        val song = plain()
        // 첫 음은 두 번째 음을 부는 중(연주 순서 1)에 확정됐다. 연주 순서 2부터 옅은 연두로 가라앉는다.
        val confirmed = tally(window(song, 0, 0.0)).confirm(song, 1, 550.0, 60)
        fun settled(state: PracticeUiState) = livePitchMarks(state.copy(song = song), confirmed).values.map { it.settled }.distinct()
        assertEquals(listOf(false), settled(recording.copy(position = 1)))
        assertEquals(listOf(true), settled(recording.copy(position = 2)))
        assertEquals(listOf(true), settled(recording.copy(position = 1, finished = true)))
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

    @Test fun keepsConfirmedNotesColouredOnEveryCellOfTheNote() {
        val song = plain()
        // 두 번째 음(+200)은 조율 추정에서 빠지고 다른 음이다. 두 정간에 걸친 표시 자리를 모두 칠한다.
        val counted = tally(window(song, 0, 0.0), window(song, 1, 200.0)).confirmBefore(song, 2)
        val marks = livePitchMarks(recording.copy(song = song, position = 2), counted)
        assertEquals(keysOf(song, 0).associateWith { LivePitchCell(LivePitchMark.Correct) } +
            keysOf(song, 1).associateWith { LivePitchCell(LivePitchMark.Wrong) }, marks)
        assertEquals(marks, livePitchMarks(recording.copy(song = song, position = 2, mode = PracticeMode.Interrupted), counted))
    }

    @Test fun leavesRestOrnamentUnconfirmedPitchAndSoloPracticeEmpty() {
        val silent = window(song, 0, 0.0).current!!.copy(heardHz = null)
        assertEquals(emptyMap(), tally(PracticePitchFeedback(current = silent)).marks(song))
        // 작은 악보의 두 번째 음은 꾸밈음(니레)이 있어 서버처럼 음정을 판정하지 않는다.
        assertEquals(emptyMap(), livePitchMarks(recording, tally(window(song, 1, 300.0)).confirmBefore(song, 3)))
        val confirmed = tally(window(song, 0, 0.0)).confirmBefore(song, 1)
        assertEquals(keysOf(song, 0), livePitchMarks(recording, confirmed).keys)
        assertEquals(emptyMap(), livePitchMarks(recording.copy(solo = true), confirmed))
        // ‘실시간 채점’을 끄면 색만 숨기고, 다시 켜면 모은 판정이 그대로 보인다(S15P31E102-980).
        assertEquals(emptyMap(), livePitchMarks(recording.copy(settings = PracticeSettings(liveMarks = false)), confirmed))
        assertEquals(keysOf(song, 0), livePitchMarks(recording.copy(settings = PracticeSettings(liveMarks = true)), confirmed).keys)
        assertEquals(emptyMap(), livePitchMarks(recording.copy(song = null), confirmed))
    }
}

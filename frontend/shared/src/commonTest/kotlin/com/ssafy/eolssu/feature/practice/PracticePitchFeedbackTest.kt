package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PracticePitchFeedbackTest {
    private val events = List(3) { i -> ScoreEvent(i, "event-$i", 0, i, 0, 1,
        ScoreNote.SourcePitch("황", 0), i.toDouble(), 1.0, i * 100L, 100L) }
    private val boundary = CaptureBoundary(1, 1, 0, 1000, 1_000_000_000, true)
    private fun reading(start: Long, end: Long, segment: Long = 1) =
        PitchReading(1, segment, 1, start, end, 1000, 440.0, 1_000_000_000 + end * 1_000_000)

    @Test fun keepsDelayedShortNoteOnItsOwnEventAndExpiresFromInputTime() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        val result = feedback.accept(reading(20, 66), 1, 1_166_000_000)
        assertEquals("event-0", result.previous?.eventId)
        assertEquals(true, result.previous?.correct)
        assertNull(result.current)
        assertNull(feedback.expire(1, 1_367_000_000).previous)
    }

    @Test fun rejectsBoundaryWindowsOldSegmentsAndUnknownTimestamps() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        assertNull(feedback.accept(reading(80, 120), 1, 1_120_000_000).current)
        assertNull(feedback.accept(reading(20, 66, 2), 0, 1_066_000_000).current)
        feedback.anchor(boundary.copy(timestampValid = false), 0.0)
        assertNull(feedback.accept(reading(20, 66), 0, 1_066_000_000).current)
        feedback.anchor(boundary.copy(analysisAvailable = false), 0.0)
        assertNull(feedback.accept(reading(20, 66), 0, 1_066_000_000).current)
    }

    @Test fun pauseResumeUsesFileSamplesWithoutAddingPauseTime() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        repeat(10) { i ->
            val cut = boundary.copy(segmentId = i + 1L, samplePosition = (i + 1L) * 10)
            assertEquals((i + 1) * 10.0, feedback.positionAt(cut))
            feedback.anchor(cut.copy(segmentId = i + 2L), feedback.positionAt(cut)!!)
        }
    }

    @Test fun restAndOrnamentsCannotBeMarkedAsMatchingPitch() {
        val special = listOf(events[0].copy(note = ScoreNote.Rest), events[1].copy(sigimsae = listOf("떠는표")), events[2])
        val feedback = PracticePitchTracker(special, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        val rest = feedback.accept(reading(20, 66), 0, 1_066_000_000)
        assertEquals(true, rest.current?.rest)
        assertNull(rest.current?.correct)
        val ornament = feedback.accept(reading(120, 166).copy(sequence = 2), 1, 1_166_000_000)
        assertEquals("event-1", ornament.current?.eventId)
        assertNull(ornament.current?.correct)
    }

    @Test fun ignoresDifferentRecordingsAndClearsUnavailableAnalysis() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        assertNull(feedback.accept(reading(20, 66).copy(recordingId = 9), 0, 1_066_000_000).current)
        assertEquals(true, feedback.accept(reading(20, 66), 0, 1_066_000_000).current?.correct)
        assertNull(feedback.accept(reading(30, 76).copy(sequence = 2, analysisAvailable = false), 0, 1_076_000_000).current)
    }

    private val hwang = 311.13
    private fun hz(semitone: Int) = hwang * 2.0.pow(semitone / 12.0)
    private fun shifted(semitone: Int, cents: Int) = hz(semitone) * 2.0.pow(cents / 1200.0)
    // 단소 아리랑처럼 황·태·중·임·무(반음 0·2·5·7·10)를 쓰는 곡. 가장 가까운 두 음이 2반음이다.
    private val pentatonic = listOf(0, 2, 5, 7, 10).map(::hz)
    private fun note(yul: String, octave: Int = 0) = ScoreNote.SourcePitch(yul, octave)

    @Test fun correctMeansWithinHalfTheGapToTheNeighbouringScoreNotes() {
        // S15P31E102-566 D-1: 태(2) 아래 이웃 황(0)까지 200센트라 절반 100. 위 이웃 중(5)까지 300센트라 절반 150이지만 상한 100이다.
        val tae = note("태")
        assertEquals(true, comparePitch(tae, hwang, shifted(2, -99), pentatonic))
        assertEquals(false, comparePitch(tae, hwang, shifted(2, -101), pentatonic))
        assertEquals(true, comparePitch(tae, hwang, shifted(2, 99), pentatonic))
        assertEquals(false, comparePitch(tae, hwang, shifted(2, 101), pentatonic))
        // 실제 녹음의 조율 차이(+45·−25센트)는 맞음이다.
        assertEquals(true, comparePitch(tae, hwang, shifted(2, 45), pentatonic))
        assertEquals(true, comparePitch(tae, hwang, shifted(2, -25), pentatonic))
        // 다른 음(중)을 불면 틀림이다.
        assertEquals(false, comparePitch(tae, hwang, hz(5), pentatonic))
    }

    @Test fun semitoneNeighboursNarrowOnlyThatSide() {
        // D-2: 49번 곡처럼 남(9)과 무(10)가 반음 이웃이면 남의 위쪽만 50센트다.
        val song = listOf(0, 2, 5, 7, 9, 10).map(::hz)
        assertEquals(true, comparePitch(note("남"), hwang, shifted(9, 49), song))
        assertEquals(false, comparePitch(note("남"), hwang, shifted(9, 51), song))
        assertEquals(true, comparePitch(note("남"), hwang, shifted(9, -99), song))
    }

    @Test fun edgeNotesUseAHundredCentsOnTheOpenSide() {
        // 곡의 가장 높은 음 무(10)는 위쪽 이웃이 없어 100센트다.
        assertEquals(true, comparePitch(note("무"), hwang, shifted(10, 99), pentatonic))
        assertEquals(false, comparePitch(note("무"), hwang, shifted(10, 101), pentatonic))
        // 곡 정보가 없으면 양쪽 모두 100센트다.
        assertEquals(true, comparePitch(note("황"), 440.0, 440.0))
        assertNull(comparePitch(note("황"), 440.0, null))
        assertNull(comparePitch(ScoreNote.Rest, 440.0, 440.0))
    }

    @Test fun octaveMistakesAreWrong() {
        // D-3: 높이 보정 뒤 남은 옥타브 차이(1,200센트 ±100)는 틀림이다. 띠 밖으로 600센트를 넘으면 보류다.
        val jung = note("중")
        fun at(cents: Int) = comparePitch(jung, hwang, shifted(5, cents), pentatonic)
        assertNull(at(1099))
        assertEquals(false, at(1101))
        assertEquals(false, at(1299))
        assertNull(at(1301))
        assertEquals(false, at(599))
        assertNull(at(601))
        // 아래 옥타브는 곡 최저음보다 높아야 낼 수 있는 실수라, 두 옥타브를 쓰는 곡의 청중(17)으로 본다.
        val twoOctaves = pentatonic + listOf(12, 14, 17, 19, 22).map(::hz)
        fun low(cents: Int) = comparePitch(note("중", 1), hwang, shifted(17, cents), twoOctaves)
        assertNull(low(-1099))
        assertEquals(false, low(-1101))
        assertEquals(false, low(-1299))
        assertNull(low(-1301))
    }

    @Test fun notesTheSongDoesNotUseAreWrongEvenWhenScoreNotesAreFarApart() {
        // D-1 상한: 황과 청중만 쓰는 곡에서 황 자리에 중(+500센트)을 불어도 맞음이 되지 않는다.
        val sparse = listOf(hz(0), hz(17))
        assertEquals(false, comparePitch(note("황"), hwang, hz(5), sparse))
        assertEquals(true, comparePitch(note("황"), hwang, shifted(0, 99), sparse))
        assertEquals(false, comparePitch(note("황"), hwang, shifted(0, 101), sparse))
    }

    @Test fun measurementErrorsStayUndecided() {
        // D-4: 515 실기기 로그의 중(415Hz) 안 140Hz·84Hz(1/3·1/5)와 곡 최저음보다 200센트 넘게 낮은 값은 보류다.
        assertNull(comparePitch(note("중"), hwang, 140.0, pentatonic))
        assertNull(comparePitch(note("중"), hwang, 84.0, pentatonic))
        assertEquals(false, comparePitch(note("황"), hwang, shifted(0, -199), pentatonic))
        assertNull(comparePitch(note("황"), hwang, shifted(0, -201), pentatonic))
    }

    @Test fun trackerUsesTheSongsOwnNotes() {
        // 곡의 음이 모두 황(440Hz 기준)이면 220Hz는 최저음 아래라 보류, 470Hz(+114센트)는 위 이웃이 없어 100센트 밖이라 틀림이다.
        val feedback = PracticePitchTracker(events, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        val low = feedback.accept(reading(20, 66).copy(frequencyHz = 220.0), 0, 1_066_000_000)
        assertEquals("event-0", low.current?.eventId)
        assertNull(low.current?.correct)
        val high = feedback.accept(reading(30, 76).copy(sequence = 2, frequencyHz = 470.0), 0, 1_076_000_000)
        assertEquals(false, high.current?.correct)
    }

    // 대금 낮은 옥타브(S15P31E102-892). 곡의 음이 모두 황(440Hz)이면 하한은 그 200센트 아래(약 392Hz)다.
    private val floor = 440.0 * 2.0.pow(-2 / 12.0)

    @Test fun daegeumBlownAnOctaveLowIsDoubledBeforeJudging() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0, lowOctaveFloorHz = floor)
        feedback.anchor(boundary, 0.0)
        val low = feedback.accept(reading(20, 66).copy(frequencyHz = 220.0), 0, 1_066_000_000).current
        assertEquals(true, low?.correct)
        assertEquals(440.0, low?.heardHz) // 화면 색 집계가 고친 높이로 계산하도록 고친 값을 넘긴다
    }

    @Test fun aWrongNoteBlownLowIsStillWrongAfterDoubling() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0, lowOctaveFloorHz = floor)
        feedback.anchor(boundary, 0.0)
        val wrong = feedback.accept(reading(20, 66).copy(frequencyHz = 247.0), 0, 1_066_000_000).current
        assertEquals(false, wrong?.correct) // 494Hz, 황보다 200센트 높음
        assertEquals(494.0, wrong?.heardHz)
    }

    @Test fun valuesStillBelowTheFloorAfterDoublingStayUndecidedWithTheirMeasuredHeight() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0, lowOctaveFloorHz = floor)
        feedback.anchor(boundary, 0.0)
        val tooLow = feedback.accept(reading(20, 66).copy(frequencyHz = 180.0), 0, 1_066_000_000).current
        assertNull(tooLow?.correct)
        assertEquals(180.0, tooLow?.heardHz)
    }

    @Test fun withoutAFloorLowValuesAreNotTouched() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        val low = feedback.accept(reading(20, 66).copy(frequencyHz = 220.0), 0, 1_066_000_000).current
        assertNull(low?.correct)
        assertEquals(220.0, low?.heardHz)
    }

    @Test fun onlyDaegeumGetsAFloorFromItsLowestNote() {
        val notes = listOf(note("태", 1), note("황", 1), note("고", 1)).mapIndexed { i, n -> events[i].copy(note = n) }
        assertEquals(hz(12) * 2.0.pow(-2 / 12.0), lowOctaveFloorHz(notes, hwang, "JEONGAK_DAEGEUM")!!, 0.001)
        assertEquals(hz(12) * 2.0.pow(-2 / 12.0), lowOctaveFloorHz(notes, hwang, "SANJO_DAEGEUM")!!, 0.001)
        assertNull(lowOctaveFloorHz(notes, hwang, "DANSO"))
        assertNull(lowOctaveFloorHz(notes, hwang, null))
        assertNull(lowOctaveFloorHz(emptyList(), hwang, "JEONGAK_DAEGEUM"))
    }
}

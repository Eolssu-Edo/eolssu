package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
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
        assertEquals(PitchDirection.Match, result.previous?.direction)
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
        assertNull(rest.current?.direction)
        val ornament = feedback.accept(reading(120, 166).copy(sequence = 2), 1, 1_166_000_000)
        assertEquals("event-1", ornament.current?.eventId)
        assertNull(ornament.current?.direction)
    }

    @Test fun ignoresDifferentRecordingsAndClearsUnavailableAnalysis() {
        val feedback = PracticePitchTracker(events, 440.0, 1.0)
        feedback.anchor(boundary, 0.0)
        assertNull(feedback.accept(reading(20, 66).copy(recordingId = 9), 0, 1_066_000_000).current)
        assertEquals(PitchDirection.Match, feedback.accept(reading(20, 66), 0, 1_066_000_000).current?.direction)
        assertNull(feedback.accept(reading(30, 76).copy(sequence = 2, analysisAvailable = false), 0, 1_076_000_000).current)
    }

    @Test fun comparesPitchWithoutFoldingOctaves() {
        val note = ScoreNote.SourcePitch("황", 0)
        assertEquals(PitchDirection.Match, comparePitch(note, 440.0, 440.0))
        assertEquals(PitchDirection.High, comparePitch(note, 440.0, 880.0))
        assertEquals(PitchDirection.Low, comparePitch(note, 440.0, 220.0))
        assertNull(comparePitch(note, 440.0, null))
        assertNull(comparePitch(ScoreNote.Rest, 440.0, 440.0))
    }
}

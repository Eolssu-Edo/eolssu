package com.ssafy.eolssu.backend.feature.evaluation

import com.ssafy.eolssu.backend.feature.evaluation.domain.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class SectionComparisonTests {
    private val time = Instant.parse("2026-10-29T00:00:00Z")
    private fun session(start: Int, end: Int, total: Int, scoreVersion: Int = 1, model: String = "m1", bpm: Int = 60) =
        PracticeEvaluationSnapshot(1, 1, scoreVersion, bpm, "QUARTER", "DONE", time, time, 1, total, total, total, total, time, null,
            start, end, parentSessionId = null, modelVersion = model)
    private fun notes(range: IntRange, vararg wrong: Pair<Int, String>) = range.map { idx ->
        PracticeEvaluationNote(idx, wrong.toMap()[idx] ?: "CORRECT", null, null, null, 1, null)
    }

    @Test fun `same section retake compares error counts and note changes`() {
        val result = SectionComparison.compare(
            session(2, 5, 85), notes(2..5, 4 to "TIMING"),
            session(2, 5, 68), notes(2..5, 2 to "PITCH", 3 to "MISSED", 4 to "PITCH"),
        )
        assertNull(result.notComparable)
        assertEquals(3, result.beforeErrorCount)
        assertEquals(SectionScores(85, 85, 85, 85, 1), result.after)
        assertEquals(SectionDelta(-2), result.delta)
        assertEquals(listOf(2, 3), result.correctedNoteIndices)
        assertEquals(listOf(4), result.remainingErrors.map { it.noteIdx })
    }

    @Test fun `whole song baseline is compared on the section notes`() {
        val result = SectionComparison.compare(
            session(2, 5, 90), notes(2..5),
            session(0, 9, 70), notes(0..9, 0 to "PITCH", 3 to "TIMING", 8 to "MISSED"),
        )
        assertNull(result.notComparable)
        assertEquals(1, result.beforeErrorCount)
        assertEquals(SectionDelta(-1), result.delta)
        assertEquals(listOf(3), result.correctedNoteIndices)
    }

    @Test fun `different model or tempo keeps both results without delta`() {
        val model = SectionComparison.compare(session(0, 3, 80, model = "m2"), notes(0..3), session(0, 3, 70), notes(0..3))
        assertEquals(NotComparable.MODEL, model.notComparable)
        assertEquals(0, model.beforeErrorCount)
        assertNull(model.delta)
        val tempo = SectionComparison.compare(session(0, 3, 80, bpm = 48), notes(0..3), session(0, 3, 70), notes(0..3))
        assertEquals(NotComparable.TEMPO, tempo.notComparable)
        assertNull(tempo.delta)
    }

    @Test fun `whole song baseline on another model reports the model not the range`() {
        val result = SectionComparison.compare(session(2, 3, 80, model = "m2"), notes(2..3), session(0, 9, 70), notes(0..9, 2 to "PITCH"))
        assertEquals(NotComparable.MODEL, result.notComparable)
        assertEquals(1, result.beforeErrorCount)
        assertNull(result.delta)
    }

    @Test fun `changed score version does not match notes by index`() {
        val result = SectionComparison.compare(
            session(0, 3, 80, scoreVersion = 2), notes(0..3), session(0, 3, 70), notes(0..3, 1 to "PITCH"),
        )
        assertEquals(NotComparable.SCORE_VERSION, result.notComparable)
        assertNull(result.beforeErrorCount)
        assertEquals(emptyList(), result.correctedNoteIndices)
    }

    @Test fun `older score baseline is not checked against the new note indices`() {
        val result = SectionComparison.compare(
            session(2, 3, 80, scoreVersion = 2), notes(2..3, 3 to "PITCH"), session(0, 1, 70), notes(0..1, 1 to "PITCH"),
        )
        assertEquals(NotComparable.SCORE_VERSION, result.notComparable)
        assertNull(result.beforeErrorCount)
        assertEquals(listOf(3), result.remainingErrors.map { it.noteIdx })
    }
}

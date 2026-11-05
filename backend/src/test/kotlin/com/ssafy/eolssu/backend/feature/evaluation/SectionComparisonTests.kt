package com.ssafy.eolssu.backend.feature.evaluation

import com.ssafy.eolssu.backend.feature.evaluation.domain.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SectionComparisonTests {
    private val time = Instant.parse("2026-10-29T00:00:00Z")
    private fun session(start: Int, end: Int, total: Int, scoreVersion: Int = 1, model: String = "m1", bpm: Int = 60, unit: String = "QUARTER") =
        PracticeEvaluationSnapshot(1, 1, scoreVersion, bpm, unit, "DONE", time, time, 1, total, total, total, total, time, null,
            start, end, parentSessionId = null, modelVersion = model)
    private fun notes(range: IntRange, vararg wrong: Pair<Int, String>) = range.map { idx ->
        PracticeEvaluationNote(idx, wrong.toMap()[idx] ?: "CORRECT", null, null, null, 1, null)
    }

    @Test fun `same section retake compares error counts and note changes`() {
        val result = SectionComparison.compare(
            session(2, 5, 85), notes(2..5, 4 to "TIMING"),
            session(2, 5, 68), notes(2..5, 2 to "PITCH", 3 to "MISSED", 4 to "PITCH"),
        )
        assertTrue(result.errorComparable)
        assertEquals(3, result.beforeErrorCount)
        assertEquals(SectionScores(85, 85, 85, 85, 1), result.after)
        assertEquals(-2, result.errorDelta)
        assertEquals(listOf(2, 3), result.correctedNoteIndices)
        assertEquals(listOf(4), result.remainingErrors.map { it.noteIdx })
    }

    @Test fun `whole song baseline is compared on the section notes`() {
        val result = SectionComparison.compare(
            session(2, 5, 90), notes(2..5),
            session(0, 9, 70), notes(0..9, 0 to "PITCH", 3 to "TIMING", 8 to "MISSED"),
        )
        assertEquals(1, result.beforeErrorCount)
        assertEquals(-1, result.errorDelta)
        assertEquals(listOf(3), result.correctedNoteIndices)
    }

    @Test fun `scores stay not comparable until note scores are stored`() {
        val result = SectionComparison.compare(session(0, 3, 80), notes(0..3), session(0, 3, 70), notes(0..3))
        assertFalse(result.scoreComparable)
        assertEquals(listOf(NotComparableReason.NO_NOTE_SCORES), result.notComparableReasons)
    }

    @Test fun `different tempo still compares errors and reports the change`() {
        val result = SectionComparison.compare(
            session(0, 3, 80, bpm = 48), notes(0..3), session(0, 3, 70), notes(0..3, 1 to "PITCH"),
        )
        assertTrue(result.bpmChanged)
        assertTrue(result.errorComparable)
        assertEquals(-1, result.errorDelta)
        assertEquals(listOf(NotComparableReason.NO_NOTE_SCORES), result.notComparableReasons)
    }

    @Test fun `different tempo unit also counts as a tempo change`() {
        val result = SectionComparison.compare(session(0, 3, 80, unit = "JEONGGAN"), notes(0..3), session(0, 3, 70), notes(0..3))
        assertTrue(result.bpmChanged)
    }

    @Test fun `different model blocks scores but not error counts`() {
        val result = SectionComparison.compare(
            session(2, 3, 80, model = "m2"), notes(2..3), session(0, 9, 70), notes(0..9, 2 to "PITCH"),
        )
        assertEquals(listOf(NotComparableReason.MODEL_CHANGED, NotComparableReason.NO_NOTE_SCORES), result.notComparableReasons)
        assertTrue(result.errorComparable)
        assertEquals(1, result.beforeErrorCount)
        assertEquals(-1, result.errorDelta)
    }

    @Test fun `changed score version does not match notes by index`() {
        val result = SectionComparison.compare(
            session(0, 3, 80, scoreVersion = 2), notes(0..3), session(0, 3, 70), notes(0..3, 1 to "PITCH"),
        )
        assertEquals(listOf(NotComparableReason.SCORE_VERSION_CHANGED, NotComparableReason.NO_NOTE_SCORES), result.notComparableReasons)
        assertFalse(result.errorComparable)
        assertNull(result.beforeErrorCount)
        assertNull(result.errorDelta)
        assertEquals(emptyList(), result.correctedNoteIndices)
    }

    @Test fun `older score baseline is not checked against the new note indices`() {
        val result = SectionComparison.compare(
            session(2, 3, 80, scoreVersion = 2), notes(2..3, 3 to "PITCH"), session(0, 1, 70), notes(0..1, 1 to "PITCH"),
        )
        assertFalse(result.errorComparable)
        assertNull(result.beforeErrorCount)
        assertEquals(listOf(3), result.remainingErrors.map { it.noteIdx })
    }

    @Test fun `first performance has only this result`() {
        val result = SectionComparison.compare(session(2, 5, 85), notes(2..5, 4 to "TIMING"), null)
        assertFalse(result.scoreComparable)
        assertFalse(result.errorComparable)
        assertFalse(result.bpmChanged)
        assertEquals(emptyList(), result.notComparableReasons)
        assertNull(result.beforeErrorCount)
        assertNull(result.errorDelta)
        assertEquals(emptyList(), result.correctedNoteIndices)
        assertEquals(SectionScores(85, 85, 85, 85, 1), result.after)
        assertEquals(listOf(4), result.remainingErrors.map { it.noteIdx })
    }
}

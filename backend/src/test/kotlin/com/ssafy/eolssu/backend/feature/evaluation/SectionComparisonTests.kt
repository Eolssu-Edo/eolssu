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

    private val weights = ScoreWeights(0.5, 0.25, 0.25)
    private fun rated(start: Int, end: Int, total: Int = 80, version: String = "note-mean-1", model: String = "m1", scoreVersion: Int = 1) =
        session(start, end, total, scoreVersion, model).copy(scoringVersion = version, scoreWeights = weights)
    private fun note(idx: Int, pitch: Double?, timing: Double, duration: Double, weight: Double = 1.0, verdict: String = "CORRECT") =
        PracticeEvaluationNote(idx, verdict, null, null, null, 1, null, null, pitch, timing, duration, weight)

    @Test fun `section score merges only the notes of the section with their weights`() {
        val result = SectionComparison.compare(
            rated(1, 3).copy(totalScore = 85, pitchScore = 90, timingScore = 80, durationScore = 70),
            listOf(note(1, 90.0, 90.0, 90.0), note(2, null, 90.0, 90.0), note(3, 90.0, 90.0, 90.0)),
            rated(0, 3),
            listOf(note(0, 100.0, 100.0, 100.0), note(1, 80.0, 60.0, 40.0), note(2, null, 90.0, 50.0, 3.0), note(3, 0.0, 0.0, 0.0)),
        )
        assertTrue(result.scoreComparable)
        assertEquals(emptyList(), result.notComparableReasons)
        // 음정 (80+0)/2=40 (시김새 제외), 박자 (60+270+0)/5=66, 길이 (40+150+0)/5=38, 총점 40×0.5+66×0.25+38×0.25=46
        assertEquals(SectionItemScores(46, 40, 66, 38), result.beforeScores)
        assertEquals(SectionItemScores(39, 50, 14, 32), result.scoreDelta)
    }

    @Test fun `section score rounds halves to the even side like the AI`() {
        val before = listOf(note(0, 100.0, 50.0, 50.0)) + (1..7).map { note(it, 0.0, 50.0, 50.0) }
        val result = SectionComparison.compare(rated(0, 7), (0..7).map { note(it, 50.0, 50.0, 50.0) }, rated(0, 7), before)
        assertEquals(12, result.beforeScores?.pitchScore) // 12.5
    }

    @Test fun `shared vector gives the same scores as the AI`() {
        // AI test_scoring.py의 공통 벡터와 같은 값이다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
        // 세 항목 모두 평균이 x.5인데 차례 합의 끝자리 오차로 정확한 합·역순 합과 반올림이 갈린다(62, 40, 44가 나오면 틀렸다).
        val pitch = listOf(49.0, 90.0, 61.0, null, 58.3, 81.3, 96.8, 1.1)
        val timing = listOf(25.0, 48.9, 59.0, 42.7, 71.3, 34.1, 33.7, 9.3)
        val duration = listOf(70.8, 48.1, 62.0, 18.1, 15.1, 55.6, 65.6, 12.7)
        val before = (0..7).map { note(it, pitch[it], timing[it], duration[it]) }
        val result = SectionComparison.compare(rated(0, 7), (0..7).map { note(it, 50.0, 50.0, 50.0) }, rated(0, 7), before)
        // 총점 52.5는 짝수 쪽으로 간다.
        assertEquals(SectionItemScores(52, 63, 41, 43), result.beforeScores)
    }

    @Test fun `total adds pitch timing and duration in the same order as the AI`() {
        // AI test_scoring.py의 공통 벡터와 같은 값이다. 차례 합은 6.5(짝수 쪽 6)이고 역순·보정 합산은 끝자리 오차로 7이 된다.
        val weighted = rated(0, 0).copy(scoreWeights = ScoreWeights(0.7, 0.2, 0.1))
        val result = SectionComparison.compare(weighted, listOf(note(0, 50.0, 50.0, 50.0)), weighted, listOf(note(0, 1.0, 1.0, 56.0)))
        assertEquals(SectionItemScores(6, 1, 1, 56), result.beforeScores)
    }

    @Test fun `section of only sigimsae notes has a full pitch score like the AI`() {
        val result = SectionComparison.compare(rated(0, 0), listOf(note(0, null, 50.0, 50.0)), rated(0, 0), listOf(note(0, null, 50.0, 50.0)))
        assertEquals(100, result.beforeScores?.pitchScore)
    }

    @Test fun `different scoring rule blocks scores but not error counts`() {
        val result = SectionComparison.compare(
            rated(0, 0, version = "note-mean-2"), listOf(note(0, 90.0, 90.0, 90.0)), rated(0, 0), listOf(note(0, 80.0, 80.0, 80.0)),
        )
        assertFalse(result.scoreComparable)
        assertEquals(listOf(NotComparableReason.SCORING_VERSION_CHANGED), result.notComparableReasons)
        assertNull(result.beforeScores)
        assertNull(result.scoreDelta)
        assertTrue(result.errorComparable)
    }

    @Test fun `each difference is reported when note scores exist on both sides`() {
        val model = SectionComparison.compare(rated(0, 0, model = "m2"), listOf(note(0, 90.0, 90.0, 90.0)), rated(0, 0), listOf(note(0, 80.0, 80.0, 80.0)))
        assertEquals(listOf(NotComparableReason.MODEL_CHANGED), model.notComparableReasons)
        assertNull(model.beforeScores)
        val score = SectionComparison.compare(rated(0, 0, scoreVersion = 2), listOf(note(0, 90.0, 90.0, 90.0)), rated(0, 0), listOf(note(0, 80.0, 80.0, 80.0)))
        assertEquals(listOf(NotComparableReason.SCORE_VERSION_CHANGED), score.notComparableReasons)
        assertNull(score.beforeScores)
    }

    @Test fun `an evaluation without note scores on either side blocks scores`() {
        val old = SectionComparison.compare(rated(0, 0), listOf(note(0, 90.0, 90.0, 90.0)), session(0, 0, 70), notes(0..0))
        assertEquals(listOf(NotComparableReason.NO_NOTE_SCORES), old.notComparableReasons)
        assertFalse(old.scoreComparable)
        val current = SectionComparison.compare(session(0, 0, 70), notes(0..0), rated(0, 0), listOf(note(0, 90.0, 90.0, 90.0)))
        assertEquals(listOf(NotComparableReason.NO_NOTE_SCORES), current.notComparableReasons)
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

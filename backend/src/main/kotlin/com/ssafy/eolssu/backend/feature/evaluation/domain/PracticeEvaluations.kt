package com.ssafy.eolssu.backend.feature.evaluation.domain

import java.time.Instant

/** 소유자·미삭제 세션의 저장 평가를 읽는 계약. HTTP·MyBatis 타입은 노출하지 않는다. */
interface PracticeEvaluations {
    fun findOwned(sessionId: Long, userId: Long): PracticeEvaluationSnapshot?
    fun notes(evaluationId: Long): List<PracticeEvaluationNote>
    /** 같은 곡·본인·미삭제·DONE이고 current보다 먼저 시작한 세션 중 가장 최근 것. 같은 악보 버전이면 current 구간을 모두 담아야 한다. */
    fun findPreviousSessionId(userId: Long, current: PracticeEvaluationSnapshot): Long?
}

/** 세션에 고정한 악보 버전의 NOTE 순번별 정간 위치. 현재 곡 악보가 그 버전이 아니면 null이다. */
fun interface EvaluationScoreCells {
    fun noteCells(songId: Long, scoreVersion: Int): List<NoteCell>?
}

/** 음표가 시작하는 각(column, 0부터)과 정간(cellId). */
data class NoteCell(val column: Int, val cellId: String)

data class PracticeEvaluationSnapshot(
    val sessionId: Long, val songId: Long, val scoreVersion: Int, val bpm: Int, val bpmUnit: String,
    val status: String, val startedAt: Instant, val endedAt: Instant?, val evaluationId: Long?,
    val totalScore: Int?, val pitchScore: Int?, val timingScore: Int?, val durationScore: Int?,
    val evaluatedAt: Instant?, val recordingDurationMs: Int?, val startNoteIdx: Int, val endNoteIdx: Int,
    val tuningOffsetCent: Double? = null, val tuningOutOfRange: Boolean? = null,
    val parentSessionId: Long? = null, val modelVersion: String? = null,
    val scoringVersion: String? = null, val scoreWeights: ScoreWeights? = null,
)

/** 총점의 항목 가중치. AI가 평가 때 정한 값이다. */
data class ScoreWeights(val pitch: Double, val timing: Double, val duration: Double)

data class PracticeEvaluation(
    val sessionId: Long, val evaluationId: Long, val songId: Long, val scoreVersion: Int,
    val bpm: Int, val bpmUnit: String, val startedAt: Instant, val endedAt: Instant?,
    val evaluatedAt: Instant, val totalScore: Int, val pitchScore: Int, val timingScore: Int,
    val durationScore: Int, val notes: List<PracticeEvaluationNote>, val recordingDurationMs: Int?,
    val tuningOffsetCent: Double? = null, val tuningOutOfRange: Boolean? = null,
)

data class PracticeEvaluationNote(
    val noteIdx: Int, val verdict: String, val pitchErrorCent: Int?, val onsetErrorMs: Int?, val durationErrorMs: Int?,
    val expectedPitchId: Long, val detectedPitchId: Long?, val recordingOffsetMs: Int? = null,
    val pitchScore: Double? = null, val timingScore: Double? = null, val durationScore: Double? = null, val weight: Double? = null,
) {
    val isError: Boolean get() = verdict != Verdict.CORRECT
}

/** `note_results.verdict` 값. AI가 판정하며 서버는 다시 판정하지 않는다. */
object Verdict {
    const val CORRECT = "CORRECT"
    const val PITCH = "PITCH"
    const val TIMING = "TIMING"
    const val MISSED = "MISSED"
}

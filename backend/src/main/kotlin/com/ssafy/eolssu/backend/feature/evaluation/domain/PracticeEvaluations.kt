package com.ssafy.eolssu.backend.feature.evaluation.domain

import java.time.Instant

/** 소유자·미삭제 세션의 저장 평가를 읽는 계약. HTTP·MyBatis 타입은 노출하지 않는다. */
interface PracticeEvaluations {
    fun findOwned(sessionId: Long, userId: Long): PracticeEvaluationSnapshot?
    fun notes(evaluationId: Long): List<PracticeEvaluationNote>
}

data class PracticeEvaluationSnapshot(
    val sessionId: Long, val songId: Long, val scoreVersion: Int, val bpm: Int, val bpmUnit: String,
    val status: String, val startedAt: Instant, val endedAt: Instant?, val evaluationId: Long?,
    val totalScore: Int?, val pitchScore: Int?, val timingScore: Int?, val durationScore: Int?,
    val evaluatedAt: Instant?, val recordingDurationMs: Int?,
)

data class PracticeEvaluation(
    val sessionId: Long, val evaluationId: Long, val songId: Long, val scoreVersion: Int,
    val bpm: Int, val bpmUnit: String, val startedAt: Instant, val endedAt: Instant?,
    val evaluatedAt: Instant, val totalScore: Int, val pitchScore: Int, val timingScore: Int,
    val durationScore: Int, val notes: List<PracticeEvaluationNote>, val recordingDurationMs: Int?,
)

data class PracticeEvaluationNote(
    val noteIdx: Int, val verdict: String, val pitchErrorCent: Int?, val onsetErrorMs: Int?, val durationErrorMs: Int?,
)

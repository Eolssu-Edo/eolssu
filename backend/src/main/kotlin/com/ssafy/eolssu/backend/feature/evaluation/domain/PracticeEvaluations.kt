package com.ssafy.eolssu.backend.feature.evaluation.domain

import java.time.Instant

/** 소유자·미삭제 세션의 저장 평가를 읽는 계약. HTTP·MyBatis 타입은 노출하지 않는다. */
interface PracticeEvaluations {
    fun findOwned(sessionId: Long, userId: Long): PracticeEvaluationSnapshot?
    fun notes(evaluationId: Long): List<PracticeEvaluationNote>
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
)

data class PracticeEvaluation(
    val sessionId: Long, val evaluationId: Long, val songId: Long, val scoreVersion: Int,
    val bpm: Int, val bpmUnit: String, val startedAt: Instant, val endedAt: Instant?,
    val evaluatedAt: Instant, val totalScore: Int, val pitchScore: Int, val timingScore: Int,
    val durationScore: Int, val notes: List<PracticeEvaluationNote>, val recordingDurationMs: Int?,
)

data class PracticeEvaluationNote(
    val noteIdx: Int, val verdict: String, val pitchErrorCent: Int?, val onsetErrorMs: Int?, val durationErrorMs: Int?,
    val expectedPitchId: Long, val detectedPitchId: Long?,
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

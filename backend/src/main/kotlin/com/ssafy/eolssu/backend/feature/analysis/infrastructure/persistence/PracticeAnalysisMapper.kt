package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PracticeAnalysisMapper {
    fun findPending(@Param("sessionId") sessionId: Long): PendingAnalysisRow?

    /** 같은 시도의 `PENDING`일 때만 바꾼다. 바뀐 행 수(0 또는 1)를 돌려준다. */
    fun markDone(@Param("sessionId") sessionId: Long, @Param("attempt") attempt: Int): Int

    fun markFailed(@Param("sessionId") sessionId: Long, @Param("attempt") attempt: Int, @Param("failReason") failReason: String): Int

    fun insertEvaluation(evaluation: NewEvaluation): Int

    fun insertNoteResults(@Param("evaluationId") evaluationId: Long, @Param("notes") notes: List<NewNoteResult>): Int
}

data class PendingAnalysisRow(
    val sessionId: Long,
    val attempt: Int,
    val songId: Long,
    val scoreVersion: Int,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val bpm: Int,
    val bpmUnit: String,
    val recordingUrl: String,
)

/** 저장 뒤 MyBatis가 자동 증가 `id`를 채운다. */
class NewEvaluation(
    val sessionId: Long,
    val totalScore: Int,
    val pitchScore: Int,
    val timingScore: Int,
    val durationScore: Int,
    val modelVersion: String,
) {
    var id: Long? = null
}

data class NewNoteResult(
    val noteIdx: Int,
    val expectedPitchId: Long,
    val pitchErrorCent: Int?,
    val onsetErrorMs: Int?,
    val durationErrorMs: Int?,
    val verdict: String,
)

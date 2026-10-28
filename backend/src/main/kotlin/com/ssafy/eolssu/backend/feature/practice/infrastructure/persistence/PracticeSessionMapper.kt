package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PracticeSessionMapper {
    fun findById(@Param("id") id: Long): PracticeSessionRow?

    fun failRecordingStartedBefore(@Param("cutoff") cutoff: LocalDateTime, @Param("failReason") failReason: String): Int

    /** 회원 본인의 세션과 평가 ID. 다른 회원의 세션이면 null. */
    fun findDetail(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): PracticeSessionDetailRow?
}

data class PracticeSessionDetailRow(
    val sessionId: Long,
    val userId: Long,
    val songId: Long,
    val bpm: Int,
    val bpmUnit: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val status: String,
    val evaluationId: Long?,
    val failReason: String?,
    val recordingAvailable: Boolean,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val analysisStartedAt: LocalDateTime?,
    val savedAt: LocalDateTime?,
    val memo: String?,
)

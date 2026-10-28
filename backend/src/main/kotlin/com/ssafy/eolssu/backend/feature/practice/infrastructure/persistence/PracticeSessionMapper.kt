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

    /** 업로드 판정용 상태. 다른 회원의 세션이면 null. */
    fun findUploadTarget(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): RecordingUploadTargetRow?

    /** `RECORDING`일 때만 원본·메타데이터·`ended_at`을 기록하고 `PENDING`으로 바꾸며 분석 시도를 시작한다. 바뀐 행 수. */
    fun markRecordingUploaded(
        @Param("sessionId") sessionId: Long,
        @Param("userId") userId: Long,
        @Param("recordingUrl") recordingUrl: String,
        @Param("bytes") bytes: Long,
        @Param("durationMs") durationMs: Long,
        @Param("sampleRate") sampleRate: Int?,
        @Param("channels") channels: Int?,
        @Param("uploadedAt") uploadedAt: LocalDateTime,
    ): Int
}

data class RecordingUploadTargetRow(
    val status: String,
    val failReason: String?,
    val startedAt: LocalDateTime,
)

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

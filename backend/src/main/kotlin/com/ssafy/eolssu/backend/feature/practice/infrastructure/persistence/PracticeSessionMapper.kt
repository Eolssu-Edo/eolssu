package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PracticeSessionMapper {
    fun findById(@Param("id") id: Long): PracticeSessionRow?

    fun failRecordingStartedBefore(@Param("cutoff") cutoff: LocalDateTime, @Param("failReason") failReason: String): Int

    /** 저장하지 않았고 `ended_at`이 cutoff보다 이른 녹음. id 오름차순. */
    fun findExpiredUnsavedRecordings(
        @Param("cutoff") cutoff: LocalDateTime,
        @Param("afterSessionId") afterSessionId: Long,
        @Param("limit") limit: Int,
    ): List<ExpiredRecordingRow>

    /** 같은 키이고 아직 저장하지 않았으며 cutoff 조건이 그대로일 때만 `recording_url`을 비운다. 바뀐 행 수. */
    fun clearUnsavedRecording(
        @Param("sessionId") sessionId: Long,
        @Param("recordingUrl") recordingUrl: String,
        @Param("cutoff") cutoff: LocalDateTime,
    ): Int

    /** `recording_url`이 비어 있고 기록을 지우지 않았을 때만 키를 되돌린다. 바뀐 행 수. */
    fun restoreRecording(@Param("sessionId") sessionId: Long, @Param("recordingUrl") recordingUrl: String): Int

    /** 회원 본인의 세션과 평가 ID. 다른 회원의 세션이나 기록을 지운 세션이면 null. */
    fun findDetail(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): PracticeSessionDetailRow?

    /** 저장했고 지우지 않은 기록의 `viewed_at`이 비어 있을 때만 채운다. 바뀐 행 수. */
    fun markViewed(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long, @Param("viewedAt") viewedAt: LocalDateTime): Int

    /** 업로드 판정용 상태. 다른 회원의 세션이면 null. */
    fun findUploadTarget(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): RecordingUploadTargetRow?

    /** `RECORDING`일 때만 원본·메타데이터·`ended_at`을 기록하고 `PENDING`으로 바꾸며 분석 시도를 시작한다. 바뀐 행 수. */
    fun markRecordingUploaded(
        @Param("sessionId") sessionId: Long,
        @Param("userId") userId: Long,
        @Param("recordingUrl") recordingUrl: String,
        @Param("bytes") bytes: Long,
        @Param("durationMs") durationMs: Long,
        @Param("sampleRate") sampleRate: Int,
        @Param("channels") channels: Int,
        @Param("uploadedAt") uploadedAt: LocalDateTime,
    ): Int

    fun findByIdempotencyKey(@Param("userId") userId: Long, @Param("idempotencyKey") idempotencyKey: String): PracticeSessionRow?

    fun findPlayableSong(@Param("songId") songId: Long): PlayableSongRow?

    fun insert(session: PracticeSessionInsert): Int
}

data class ExpiredRecordingRow(
    val sessionId: Long,
    val recordingUrl: String,
)

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
    val viewedAt: LocalDateTime?,
    val scoreVersion: Int,
    val songTitle: String,
)

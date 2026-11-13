package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PracticeAnalysisMapper {
    fun findPending(@Param("sessionId") sessionId: Long, @Param("startedAfter") startedAfter: LocalDateTime): PendingAnalysisRow?

    /** 같은 시도의 마감 전 `PENDING`일 때만 바꾼다. 바뀐 행 수(0 또는 1)를 돌려준다. */
    fun markDone(@Param("sessionId") sessionId: Long, @Param("attempt") attempt: Int, @Param("startedAfter") startedAfter: LocalDateTime): Int

    fun markFailed(
        @Param("sessionId") sessionId: Long,
        @Param("attempt") attempt: Int,
        @Param("failReason") failReason: String,
        @Param("startedAfter") startedAfter: LocalDateTime,
    ): Int

    /** 마감을 넘긴(시작 시각이 startedAfter보다 이른) `PENDING`을 `TIMEOUT`으로 바꾼다. */
    fun expireOverdue(
        @Param("sessionId") sessionId: Long,
        @Param("startedAfter") startedAfter: LocalDateTime,
        @Param("expectedAttempt") expectedAttempt: Int?,
    ): Int

    fun insertEvaluation(evaluation: NewEvaluation): Int

    fun insertNoteResults(@Param("evaluationId") evaluationId: Long, @Param("notes") notes: List<NewNoteResult>): Int

    /** 분석 음표를 만들 곡 악보. 곡이 없으면 null. */
    fun findScore(@Param("songId") songId: Long): SongScoreRow?
    fun findScoreRevision(@Param("songId") songId: Long, @Param("scoreVersion") scoreVersion: Int): SongScoreRow?

    /** 율명·옥타브를 반음·음 ID로 바꾸는 기준 데이터(V5 시드). */
    fun findPitches(): List<ScorePitchRow>

    /** 본인 세션의 분석 상태. 다른 회원의 세션이면 null. */
    fun findState(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): AnalysisStateRow?

    /** 아직 `attempt`번째 시도이고 다시 시도할 수 있는 `FAILED`일 때만 새 시도의 `PENDING`으로 바꾼다. 바뀐 행 수(0 또는 1)를 돌려준다. */
    fun startRetry(
        @Param("sessionId") sessionId: Long,
        @Param("userId") userId: Long,
        @Param("attempt") attempt: Int,
        @Param("startedAt") startedAt: LocalDateTime,
        @Param("timingId") timingId: String?,
    ): Int
}

data class SongScoreRow(
    val scoreJson: String,
    val scoreVersion: Int,
)

data class ScorePitchRow(
    val id: Long,
    val yul: String,
    val octave: Short,
    val semitone: Short,
)

data class AnalysisStateRow(
    val status: String,
    val failReason: String?,
    val attempt: Int,
    val analysisTimingId: String?,
)

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
    val instrument: String?,
)

/** 저장 뒤 MyBatis가 자동 증가 `id`를 채운다. */
class NewEvaluation(
    val sessionId: Long,
    val totalScore: Int,
    val pitchScore: Int,
    val timingScore: Int,
    val durationScore: Int,
    val modelVersion: String,
    val tuningOffsetCent: Double? = null,
    val tuningOutOfRange: Boolean? = null,
    val scoringVersion: String? = null,
    val pitchWeight: Double? = null,
    val timingWeight: Double? = null,
    val durationWeight: Double? = null,
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
    val detectedPitchId: Long? = null,
    val recordingOffsetMs: Int? = null,
    val pitchScore: Double? = null,
    val timingScore: Double? = null,
    val durationScore: Double? = null,
    val weight: Double? = null,
)

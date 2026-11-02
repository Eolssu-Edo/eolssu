package com.ssafy.eolssu.backend.feature.record.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** `practice_sessions`의 기록 칸(`saved_at`·`memo`·`viewed_at`·`record_deleted_at`)을 읽고 고친다(REC-01~04). */
@Mapper
interface PracticeRecordMapper {
    /** 본인의 지우지 않은 세션 상태를 잠그고 읽는다. 없거나 다른 회원의 세션, 기록을 지운 세션이면 null. */
    fun lockStatus(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): String?

    /** 본인의 지우지 않은 세션과 곡 제목·평가 총점. */
    fun findSession(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): RecordSessionRow?

    /** DONE이고 지우지 않은 본인 세션을 저장한다. 처음 저장할 때만 `saved_at`을 넣고, `changeMemo`면 `memo`도 바꾼다. */
    fun save(
        @Param("sessionId") sessionId: Long,
        @Param("userId") userId: Long,
        @Param("savedAt") savedAt: LocalDateTime,
        @Param("changeMemo") changeMemo: Boolean,
        @Param("memo") memo: String?,
    ): Int

    /** 기록이 있는 곡 묶음. 최근 저장 시각 내림차순, 같으면 곡 ID 내림차순. */
    fun songSummaries(@Param("userId") userId: Long): List<SongRecordSummaryRow>

    /** 한 곡의 기록. `newest`면 연주 시작 시각·세션 ID 내림차순, 아니면 오름차순. */
    fun songRecords(
        @Param("userId") userId: Long,
        @Param("songId") songId: Long,
        @Param("newest") newest: Boolean,
        @Param("offset") offset: Long,
        @Param("limit") limit: Int,
    ): List<RecordSessionRow>
}

data class SongRecordSummaryRow(
    val songId: Long,
    val title: String,
    val recordCount: Int,
    val lastSavedAt: LocalDateTime,
    val bestScore: Int,
)

data class RecordSessionRow(
    val sessionId: Long,
    val songId: Long,
    val songTitle: String,
    val noteCount: Int,
    val scoreVersion: Int,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val bpm: Int,
    val bpmUnit: String,
    val status: String,
    val startedAt: LocalDateTime,
    val recordingDurationMs: Int?,
    val totalScore: Int?,
    val savedAt: LocalDateTime?,
    val memo: String?,
    val recordingAvailable: Boolean,
    val viewedAt: LocalDateTime?,
)

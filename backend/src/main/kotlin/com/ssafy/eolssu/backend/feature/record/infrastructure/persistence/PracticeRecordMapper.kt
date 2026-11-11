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

    fun savedRecordWeek(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDateTime,
        @Param("until") until: LocalDateTime,
    ): SavedRecordWeekRow

    /** 한 곡의 기록. `newest`면 연주 시작 시각·세션 ID 내림차순, 아니면 오름차순. */
    fun songRecords(
        @Param("userId") userId: Long,
        @Param("songId") songId: Long,
        @Param("newest") newest: Boolean,
        @Param("offset") offset: Long,
        @Param("limit") limit: Int,
    ): List<RecordSessionRow>

    /** 본인 세션의 녹음 키와 길이. 저장 여부는 보지 않고, 녹음이 없거나 기록을 지웠으면 null. */
    fun findRecording(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): RecordingFileRow?

    /** 저장했고 지우지 않은 본인 기록. 아니면 null. */
    fun findDeletable(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): DeletableRecordRow?

    /** 기록을 지운다. 바뀐 행 수(그사이 지워졌으면 0). */
    fun markDeleted(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long, @Param("deletedAt") deletedAt: LocalDateTime): Int
}

data class RecordingFileRow(val recordingUrl: String, val recordingDurationMs: Int?)

// MyBatis는 모든 칸이 NULL인 행을 null로 돌려주므로 녹음이 없는 기록을 위해 세션 ID를 함께 읽는다.
data class DeletableRecordRow(val sessionId: Long, val recordingUrl: String?)

data class SongRecordSummaryRow(
    val songId: Long,
    val title: String,
    val recordCount: Int,
    val lastSavedAt: LocalDateTime,
    val bestScore: Int,
    val instrumentCode: String?,
    val lastStartedAt: LocalDateTime,
)

data class SavedRecordWeekRow(val recordCount: Int, val bestScore: Int?)

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

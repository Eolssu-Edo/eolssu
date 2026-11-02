package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.growth.domain.FullSongPlay
import java.time.LocalDate
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 성장 화면 통계 조회. 날짜는 시작 시각(UTC 저장)에 9시간을 더한 KST 날짜다. */
@Mapper
interface PracticeStatsMapper {
    fun sumCountedSecondsByDay(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<DailySecondsRow>

    fun sumDoneSessionsByDay(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<DailySessionsRow>

    fun findPracticeDays(
        @Param("userId") userId: Long,
        @Param("to") to: LocalDateTime,
        @Param("minSeconds") minSeconds: Long,
    ): List<LocalDate>

    fun findBestFullSongScores(@Param("userId") userId: Long): List<SongBestRow>

    fun findLatestFullSongPlay(@Param("userId") userId: Long): FullSongPlayRow?

    fun findFirstFullSongPlayLike(
        @Param("userId") userId: Long,
        @Param("songId") songId: Long,
        @Param("scoreVersion") scoreVersion: Int,
        @Param("modelVersion") modelVersion: String,
        @Param("bpm") bpm: Int,
        @Param("bpmUnit") bpmUnit: String,
    ): FullSongPlayRow?

    fun findPlayedSongs(@Param("userId") userId: Long): List<PlayedSongRow>

    /** [full]이 참이면 전체 곡 연주, 거짓이면 구간 연습. */
    fun findRecentScoredPlays(
        @Param("userId") userId: Long,
        @Param("songId") songId: Long,
        @Param("full") full: Boolean,
        @Param("limit") limit: Int,
    ): List<ScoredPlayRow>
}

data class PlayedSongRow(
    val songId: Long,
    val title: String,
    val lastPlayedAt: LocalDateTime,
    val fullPlayCount: Long,
    val sectionPlayCount: Long,
)

data class ScoredPlayRow(
    val sessionId: Long,
    val startedAt: LocalDateTime,
    val bpm: Int,
    val bpmUnit: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val scoreVersion: Int,
    val modelVersion: String,
    val totalScore: Int,
    val pitchScore: Int,
    val timingScore: Int,
    val durationScore: Int,
)

data class DailySecondsRow(val day: LocalDate, val total: Long)

data class SongBestRow(val songId: Long, val title: String, val bestTotalScore: Int)

data class FullSongPlayRow(
    val sessionId: Long,
    val songId: Long,
    val title: String,
    val scoreVersion: Int,
    val modelVersion: String,
    val bpm: Int,
    val bpmUnit: String,
    val pitchScore: Int,
    val timingScore: Int,
    val durationScore: Int,
) {
    fun toPlay() = FullSongPlay(sessionId, songId, title, scoreVersion, modelVersion, bpm, bpmUnit, pitchScore, timingScore, durationScore)
}

data class DailySessionsRow(val day: LocalDate, val doneCount: Long, val scoredCount: Long, val scoreSum: Long)

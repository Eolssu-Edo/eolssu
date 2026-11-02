package com.ssafy.eolssu.backend.feature.growth.domain

import java.time.Instant

/** 곡별 점수 변화의 보기(GRO-03). 전체 곡 연주와 구간 연습을 한 그래프에 섞지 않는다. */
enum class ScoreScope { FULL, SECTION }

/** 평가가 있는 분석 완료 연주 한 번. 저장 여부·기록 삭제와 관계없이 센다(docs/specs/growth-score-trend.md). */
data class ScoredPlay(
    val sessionId: Long,
    val startedAt: Instant,
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

/** 세는 연주가 있는 곡과 그 곡의 가장 최근 연주 시각, 전체 곡 연주·구간 연습 수. */
data class PlayedSong(
    val songId: Long,
    val title: String,
    val lastPlayedAt: Instant,
    val fullPlayCount: Long,
    val sectionPlayCount: Long,
)

interface ScoreTrendRepository {
    /** 세는 연주가 있는 곡. 가장 최근 연주가 늦은 곡 먼저, 같으면 곡 ID가 큰 곡 먼저. */
    fun playedSongs(userId: Long): List<PlayedSong>

    /** [songId]의 세는 연주 중 [scope]에 맞는 것을 최근 순(같은 시각이면 세션 ID가 큰 것 먼저)으로 [limit]개. */
    fun recentScoredPlays(userId: Long, songId: Long, scope: ScoreScope, limit: Int): List<ScoredPlay>
}

/** 곡의 지금 악보에서 음표 순번별 각(0부터). 지금 악보가 [scoreVersion]이 아니면 null이다. */
fun interface SongNoteColumns {
    fun columns(songId: Long, scoreVersion: Int): List<Int>?
}

package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.PlayedSong
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreScope
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreTrendRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoredPlay
import com.ssafy.eolssu.backend.feature.growth.domain.SongNoteColumns

/**
 * 연습 기록지 '점수 변화'(GRO-03). 곡 하나의 최근 세는 연주 점수를 오래된 순서로 준다.
 * 곡을 고르지 않으면 가장 최근에 연주한 곡이다. 기준은 docs/specs/growth-score-trend.md.
 */
class GetScoreTrend(
    private val repository: ScoreTrendRepository,
    private val noteColumns: SongNoteColumns,
) {
    fun trend(userId: Long, songId: Long?, scope: ScoreScope, limit: Int): ScoreTrend {
        val songs = repository.playedSongs(userId)
        // 곡 목록은 가장 최근에 연주한 곡이 먼저다.
        val target = songId ?: songs.firstOrNull()?.songId ?: return ScoreTrend(null, scope, emptyList(), false, songs)
        val plays = repository.recentScoredPlays(userId, target, scope, limit).reversed()
        val columnsByVersion = plays.map { it.scoreVersion }.distinct().associateWith { noteColumns.columns(target, it) }
        val points = plays.map { play -> ScorePoint(play, columnsByVersion[play.scoreVersion]?.let { columnRange(it, play) }) }
        return ScoreTrend(target, scope, points, comparable(plays), songs)
    }

    // 지금 악보의 음표 수를 넘는 범위(악보를 고쳐 음표가 줄어든 경우)는 각을 정할 수 없다.
    private fun columnRange(columns: List<Int>, play: ScoredPlay): ColumnRange? =
        if (play.endNoteIdx < columns.size) ColumnRange(columns[play.startNoteIdx], columns[play.endNoteIdx]) else null

    // 보여 주는 점이 2개 이상이고 악보 버전·채점 모델·BPM(단위 포함)·연주 범위가 모두 같을 때만 수치를 바로 비교할 수 있다.
    // 전체 곡 연주는 범위가 늘 같으므로 범위 조건은 구간 연습에서만 갈린다.
    private fun comparable(plays: List<ScoredPlay>): Boolean = plays.size >= 2 && plays.distinctBy { it.condition() }.size == 1

    private fun ScoredPlay.condition() = Condition(scoreVersion, modelVersion, bpm, bpmUnit, startNoteIdx, endNoteIdx)

    private data class Condition(
        val scoreVersion: Int,
        val modelVersion: String,
        val bpm: Int,
        val bpmUnit: String,
        val startNoteIdx: Int,
        val endNoteIdx: Int,
    )
}

data class ScoreTrend(
    val songId: Long?,
    val scope: ScoreScope,
    val points: List<ScorePoint>,
    val comparable: Boolean,
    val songs: List<PlayedSong>,
)

/** 연주 한 번과 그 범위의 각. 세션 악보 버전이 지금 악보와 다르면 각은 null이다. */
data class ScorePoint(val play: ScoredPlay, val columns: ColumnRange?)

/** 0부터 센 시작·끝 각(양 끝 포함). */
data class ColumnRange(val start: Int, val end: Int)

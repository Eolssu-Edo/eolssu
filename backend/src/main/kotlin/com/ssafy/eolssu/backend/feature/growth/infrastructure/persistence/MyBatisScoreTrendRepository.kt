package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.growth.domain.PlayedSong
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreScope
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreTrendRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoredPlay
import java.time.ZoneOffset

/** DB 시각은 UTC로 저장한다. */
class MyBatisScoreTrendRepository(private val mapper: PracticeStatsMapper) : ScoreTrendRepository {
    override fun playedSongs(userId: Long) = mapper.findPlayedSongs(userId).map {
        PlayedSong(it.songId, it.title, it.lastPlayedAt.toInstant(ZoneOffset.UTC), it.fullPlayCount, it.sectionPlayCount)
    }

    override fun recentScoredPlays(userId: Long, songId: Long, scope: ScoreScope, limit: Int) =
        mapper.findRecentScoredPlays(userId, songId, scope == ScoreScope.FULL, limit).map {
            ScoredPlay(
                it.sessionId, it.startedAt.toInstant(ZoneOffset.UTC), it.bpm, it.bpmUnit, it.startNoteIdx, it.endNoteIdx,
                it.scoreVersion, it.modelVersion, it.totalScore, it.pitchScore, it.timingScore, it.durationScore,
            )
        }
}

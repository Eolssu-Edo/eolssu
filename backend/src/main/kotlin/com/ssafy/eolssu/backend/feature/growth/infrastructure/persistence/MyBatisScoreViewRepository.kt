package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewSegment
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.dao.DuplicateKeyException

/** DB 시각은 UTC `LocalDateTime`으로 저장한다(README의 MySQL 세션 시간대). */
class MyBatisScoreViewRepository(private val mapper: ScoreViewMapper) : ScoreViewRepository {
    override fun songExists(songId: Long) = mapper.songExists(songId)

    override fun findByKey(userId: Long, idempotencyKey: String) =
        mapper.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.toSegment()

    override fun findLatestByKey(userId: Long, idempotencyKey: String) =
        mapper.findByUserIdAndIdempotencyKeyForShare(userId, idempotencyKey)?.toSegment()

    override fun insertIfAbsent(segment: ScoreViewSegment): Boolean = try {
        mapper.insert(
            segment.userId,
            segment.songId,
            segment.idempotencyKey,
            segment.startedAt.toUtc(),
            segment.endedAt.toUtc(),
            segment.visibleSeconds,
            segment.countedSeconds,
        )
        true
    } catch (_: DuplicateKeyException) {
        // score_views의 유일 제약은 PK와 uq_score_views_user_idempotency뿐이며 PK는 자동 증가다.
        false
    }

    private fun ScoreViewRow.toSegment() = ScoreViewSegment(
        userId, songId, idempotencyKey, startedAt.toInstant(ZoneOffset.UTC), endedAt.toInstant(ZoneOffset.UTC),
        visibleSeconds, countedSeconds,
    )

    private fun Instant.toUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}

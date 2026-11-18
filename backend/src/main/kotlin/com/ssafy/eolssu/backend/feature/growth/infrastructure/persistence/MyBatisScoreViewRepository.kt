package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewReferenceMissing
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewSegment
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.sql.SQLException
import org.springframework.dao.DataIntegrityViolationException
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
    } catch (e: DataIntegrityViolationException) {
        // 외래 키(user_id·song_id) 위반만 바꾼다. 그 밖의 제약 위반은 도메인 검증이 놓친 버그라 그대로 둔다.
        if (e.mysqlErrorCode() == MYSQL_NO_REFERENCED_ROW) throw ScoreViewReferenceMissing() else throw e
    }

    private fun Throwable.mysqlErrorCode(): Int? =
        generateSequence(this) { it.cause }.filterIsInstance<SQLException>().firstOrNull()?.errorCode

    private fun ScoreViewRow.toSegment() = ScoreViewSegment(
        userId, songId, idempotencyKey, startedAt.toInstant(ZoneOffset.UTC), endedAt.toInstant(ZoneOffset.UTC),
        visibleSeconds, countedSeconds,
    )

    private fun Instant.toUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

    private companion object {
        // ER_NO_REFERENCED_ROW_2: 자식 행이 가리키는 부모 행이 없다.
        const val MYSQL_NO_REFERENCED_ROW = 1452
    }
}

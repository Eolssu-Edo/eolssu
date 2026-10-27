package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface ScoreViewMapper {
    fun findById(@Param("id") id: Long): ScoreViewRow?

    fun findByUserIdAndIdempotencyKey(
        @Param("userId") userId: Long,
        @Param("idempotencyKey") idempotencyKey: String,
    ): ScoreViewRow?

    fun songExists(@Param("songId") songId: Long): Boolean

    fun insert(
        @Param("userId") userId: Long,
        @Param("songId") songId: Long,
        @Param("idempotencyKey") idempotencyKey: String,
        @Param("startedAt") startedAt: LocalDateTime,
        @Param("endedAt") endedAt: LocalDateTime,
        @Param("visibleSeconds") visibleSeconds: Int,
        @Param("countedSeconds") countedSeconds: Int,
    ): Int
}

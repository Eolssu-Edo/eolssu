package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PracticeSessionMapper {
    fun findById(@Param("id") id: Long): PracticeSessionRow?

    fun failRecordingStartedBefore(@Param("cutoff") cutoff: LocalDateTime, @Param("failReason") failReason: String): Int

    fun findByIdempotencyKey(@Param("userId") userId: Long, @Param("idempotencyKey") idempotencyKey: String): PracticeSessionRow?

    fun findPlayableSong(@Param("songId") songId: Long): PlayableSongRow?

    fun insert(session: PracticeSessionInsert): Int
}

package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface RefreshTokenMapper {
    fun hasActiveNonExpiringSession(@Param("userId") userId: Long, @Param("tokenHash") tokenHash: String): Boolean
    fun findById(@Param("id") id: Long): RefreshTokenRow?
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insert(token: RefreshTokenInsert): Int
    fun findByTokenHash(@Param("tokenHash") tokenHash: String): RefreshTokenRow?
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): RefreshTokenRow?
    fun revoke(@Param("id") id: Long, @Param("reason") reason: String, @Param("now") now: LocalDateTime): Int
    fun revokeAllActive(@Param("userId") userId: Long, @Param("reason") reason: String, @Param("now") now: LocalDateTime): Int
}

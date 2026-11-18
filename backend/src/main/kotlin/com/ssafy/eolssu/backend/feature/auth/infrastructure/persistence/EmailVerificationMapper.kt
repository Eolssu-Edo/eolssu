package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Options
import java.time.LocalDateTime

@Mapper
interface EmailVerificationMapper {
    fun findById(@Param("id") id: Long): EmailVerificationRow?
    fun findByIdForUpdate(@Param("id") id: Long): EmailVerificationRow?
    fun findByTokenHash(@Param("tokenHash") tokenHash: String): EmailVerificationRow?
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): EmailVerificationRow?
    fun markConsumed(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
    fun registeredEmail(@Param("email") email: String): String?
    fun memberExists(@Param("email") email: String): Boolean
    fun sendHistory(@Param("email") email: String, @Param("dayStart") dayStart: LocalDateTime): VerificationSendHistoryRow
    fun invalidatePrevious(@Param("email") email: String, @Param("purpose") purpose: String, @Param("now") now: LocalDateTime): Int
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insert(verification: EmailVerificationInsert): Int
    fun recordMismatch(@Param("id") id: Long): Int
    fun replaceUndeliveredCodeHash(@Param("id") id: Long, @Param("codeHash") codeHash: String): Int
    fun markVerified(@Param("id") id: Long, @Param("tokenHash") tokenHash: String, @Param("now") now: LocalDateTime, @Param("tokenExpiresAt") tokenExpiresAt: LocalDateTime): Int
}

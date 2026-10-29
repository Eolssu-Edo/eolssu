package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface UserMapper {
    fun findById(@Param("id") id: Long): UserRow?
    fun findByIdForUpdate(@Param("id") id: Long): UserRow?
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insert(user: UserInsert): Int
    fun emailSortKey(@Param("email") email: String): String
    fun updateNickname(@Param("id") id: Long, @Param("nickname") nickname: String): Int
    fun updatePreferences(@Param("id") id: Long, @Param("instrumentId") instrumentId: Long?, @Param("notationMode") notationMode: String?): Int
    fun findActivePasswordHash(@Param("id") id: Long): String?
    fun findActivePasswordHashForUpdate(@Param("id") id: Long): String?
    fun resetPasswordHash(@Param("id") id: Long, @Param("passwordHash") passwordHash: String): Int
    fun updatePasswordHash(@Param("id") id: Long, @Param("passwordHash") passwordHash: String): Int
    fun findStatus(@Param("id") id: Long): String?
    fun findStatusForUpdate(@Param("id") id: Long): String?
    fun markWithdrawalPending(
        @Param("id") id: Long,
        @Param("requestedAt") requestedAt: LocalDateTime,
        @Param("purgeAt") purgeAt: LocalDateTime,
    ): Int
    fun findSignInMember(@Param("email") email: String): UserRow?
    fun findSignInMemberForUpdate(@Param("email") email: String): UserRow?
    fun cancelWithdrawal(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
    fun recordLogin(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
}

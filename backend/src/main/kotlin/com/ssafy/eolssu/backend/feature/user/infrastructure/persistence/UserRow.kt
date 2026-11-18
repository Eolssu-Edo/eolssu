package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import java.time.LocalDateTime

data class UserRow(
    val id: Long,
    val email: String?,
    val passwordHash: String?,
    val nickname: String?,
    val instrumentId: Long,
    val notationMode: String,
    val profileImage: String,
    val status: String,
    val lastLoginAt: LocalDateTime?,
    val withdrawalRequestedAt: LocalDateTime?,
    val purgeScheduledAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)

class UserInsert(
    val email: String?,
    val passwordHash: String?,
    val nickname: String,
    val instrumentId: Long,
    val notationMode: String,
    val createdAt: LocalDateTime,
) {
    var id: Long = 0
}

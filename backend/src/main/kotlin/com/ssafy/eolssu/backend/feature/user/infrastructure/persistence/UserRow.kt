package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import java.time.LocalDateTime

data class UserRow(
    val id: Long,
    val email: String?,
    val passwordHash: String?,
    val nickname: String?,
    val instrumentId: Long,
    val notationMode: String,
    val lastLoginAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)

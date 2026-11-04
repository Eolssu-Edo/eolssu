package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import java.time.LocalDateTime

data class RefreshTokenRow(
    val id: Long,
    val userId: Long,
    val tokenHash: String,
    val sessionId: String,
    val expiresAt: LocalDateTime?,
    val revokedAt: LocalDateTime?,
    val revokeReason: String?,
    val createdAt: LocalDateTime,
)

class RefreshTokenInsert(val userId: Long, val tokenHash: String, val sessionId: String, val expiresAt: LocalDateTime?, val createdAt: LocalDateTime) {
    var id: Long = 0
}

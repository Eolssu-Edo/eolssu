package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import java.time.LocalDateTime

data class EmailVerificationRow(
    val id: Long,
    val email: String,
    val purpose: String,
    val codeHash: String,
    val attemptCount: Short,
    val expiresAt: LocalDateTime,
    val verifiedAt: LocalDateTime?,
    val tokenHash: String?,
    val tokenExpiresAt: LocalDateTime?,
    val consumedAt: LocalDateTime?,
    val invalidatedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)

package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

data class EmailVerification(
    val id: Long,
    val email: String,
    val purpose: VerificationPurpose,
    val codeHash: String,
    val failedAttempts: Int,
    val expiresAt: Instant,
    val verifiedAt: Instant?,
    val invalidatedAt: Instant?,
)

data class NewEmailVerification(
    val email: String,
    val purpose: VerificationPurpose,
    val codeHash: String,
    val expiresAt: Instant,
    val createdAt: Instant,
)

data class VerificationSendHistory(val lastSentAt: Instant?, val dailyCount: Int)

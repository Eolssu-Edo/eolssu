package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import java.time.LocalDateTime

class EmailVerificationInsert(
    val email: String,
    val purpose: String,
    val codeHash: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
) {
    var id: Long = 0
}

data class VerificationSendHistoryRow(val lastSentAt: LocalDateTime?, val dailyCount: Long)

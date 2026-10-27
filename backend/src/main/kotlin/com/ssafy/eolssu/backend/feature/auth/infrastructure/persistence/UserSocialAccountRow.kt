package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import java.time.LocalDateTime

data class UserSocialAccountRow(
    val id: Long,
    val userId: Long,
    val provider: String,
    val providerUserId: String,
    val createdAt: LocalDateTime,
)

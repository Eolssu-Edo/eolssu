package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import java.time.LocalDateTime

data class ScoreViewRow(
    val id: Long,
    val userId: Long,
    val songId: Long,
    val idempotencyKey: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val visibleSeconds: Int,
    val countedSeconds: Int,
    val createdAt: LocalDateTime,
)

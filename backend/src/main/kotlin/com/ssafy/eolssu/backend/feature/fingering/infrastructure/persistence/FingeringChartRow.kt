package com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence

import java.time.LocalDateTime

data class FingeringChartRow(
    val id: Long,
    val instrumentId: Long,
    val pitchId: Long,
    val imageUrl: String,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

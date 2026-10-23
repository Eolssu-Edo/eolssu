package com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence

import java.time.LocalDateTime

data class InstrumentRow(
    val id: Long,
    val code: String,
    val name: String,
    val rangeLowPitchId: Long,
    val rangeHighPitchId: Long,
    val pitchToleranceCent: Short,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

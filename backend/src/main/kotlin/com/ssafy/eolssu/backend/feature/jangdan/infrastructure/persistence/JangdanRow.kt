package com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence

import java.time.LocalDateTime

data class JangdanRow(
    val id: Long,
    val name: String,
    val pattern: String,
    val defaultBpm: Short,
    val bpmUnit: String,
    val source: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

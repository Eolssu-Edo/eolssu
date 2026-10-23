package com.ssafy.eolssu.backend.feature.notation.infrastructure.persistence

import java.time.LocalDateTime

data class NotationSymbolRow(
    val id: Long,
    val symbol: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

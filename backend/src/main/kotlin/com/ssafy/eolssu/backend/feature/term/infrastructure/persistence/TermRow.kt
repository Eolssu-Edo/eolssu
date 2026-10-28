package com.ssafy.eolssu.backend.feature.term.infrastructure.persistence

import java.time.LocalDateTime

data class TermRow(
    val id: Long,
    val code: String,
    val version: String,
    val title: String,
    val contentUrl: String,
    val isRequired: Boolean,
    val seq: Short,
    val effectiveAt: LocalDateTime,
    val createdAt: LocalDateTime,
)

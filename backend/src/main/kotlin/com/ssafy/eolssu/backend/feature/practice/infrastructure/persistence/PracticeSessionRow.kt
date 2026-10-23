package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import java.time.LocalDateTime

data class PracticeSessionRow(
    val id: Long,
    val userId: Long,
    val songId: Long,
    val bpm: Short,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val recordingUrl: String?,
    val status: String,
    val failReason: String?,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)

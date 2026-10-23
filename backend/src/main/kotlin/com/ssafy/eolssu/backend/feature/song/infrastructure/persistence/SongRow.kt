package com.ssafy.eolssu.backend.feature.song.infrastructure.persistence

import java.time.LocalDateTime

data class SongRow(
    val id: Long,
    val instrumentId: Long,
    val jangdanId: Long,
    val title: String,
    val difficulty: Short,
    val defaultBpm: Short,
    val scoreJson: String,
    val umulsaiText: String?,
    val noteCount: Int,
    val source: String?,
    val isScorable: Boolean,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)

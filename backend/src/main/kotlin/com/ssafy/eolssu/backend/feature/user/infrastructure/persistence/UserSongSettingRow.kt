package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import java.time.LocalDateTime

data class UserSongSettingRow(
    val id: Long,
    val userId: Long,
    val songId: Long,
    val bpm: Short,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

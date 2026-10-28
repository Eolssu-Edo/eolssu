package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.RefreshTokenRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class MyBatisRefreshTokenRepository(private val mapper: RefreshTokenMapper) : RefreshTokenRepository {
    override fun insert(userId: Long, tokenHash: String, expiresAt: Instant, now: Instant): Long {
        val row = RefreshTokenInsert(userId, tokenHash, expiresAt.utc(), now.utc())
        check(mapper.insert(row) == 1)
        check(row.id > 0)
        return row.id
    }

    private fun Instant.utc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}

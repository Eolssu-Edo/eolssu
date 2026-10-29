package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

/** 비밀번호 변경(USR-04) 저장소. ACTIVE 회원만 대상이다. */
interface PasswordChangeRepository {
    fun <T : Any> inTransaction(action: () -> T): T
    fun findActivePasswordHash(userId: Long): String?
    fun findActivePasswordHashForUpdate(userId: Long): String?
    fun updatePasswordHash(userId: Long, passwordHash: String)
    /** 이 회원의 쓸 수 있는 리프레시 토큰을 모두 PASSWORD_CHANGED로 폐기한다. */
    fun revokeAllRefreshTokens(userId: Long, now: Instant)
}

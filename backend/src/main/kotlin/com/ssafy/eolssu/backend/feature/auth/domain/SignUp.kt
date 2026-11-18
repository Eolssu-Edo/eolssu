package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

/** AUTH-02 요청. password는 해시 전 원문이라 data class로 두지 않는다. */
class SignUpCommand(
    val email: String,
    val password: String,
    val nickname: String,
    val verificationToken: String,
    val agreements: List<TermAgreement>,
    val instrumentId: Long?,
    val notationMode: String?,
)

data class TermAgreement(val termId: Long, val version: String, val agreed: Boolean)

/** 인증 완료 토큰으로 찾은 email_verifications 행. 가입·계정 연결에 한 번만 쓴다. */
class ConsumableVerification(
    val id: Long,
    val email: String,
    val purpose: VerificationPurpose,
    val tokenExpiresAt: Instant?,
    val consumedAt: Instant?,
    val invalidatedAt: Instant?,
)

class NewUser(val email: String?, val passwordHash: String?, val nickname: String, val instrumentId: Long, val notationMode: String)

/** 같은 이메일이 동시에 가입해 users.email 유일키에 걸렸다. */
class EmailAlreadyRegistered : RuntimeException("Email already registered")

interface SignUpRepository {
    fun <T : Any> inTransaction(action: () -> T): T
    fun findVerificationForUpdate(tokenHash: String): ConsumableVerification?
    fun markConsumed(verificationId: Long, now: Instant)
    fun memberExists(email: String): Boolean
    fun activeInstrumentExists(id: Long): Boolean
    fun activeInstrumentIdByCode(code: String): Long?
    fun insertUser(user: NewUser, now: Instant): Long
    fun insertAgreements(userId: Long, agreements: List<TermAgreement>, now: Instant)
}

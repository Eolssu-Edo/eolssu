package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

interface EmailVerificationRepository {
    /** 같은 이메일의 발송·확인을 직렬화하고 결과를 반환하기 전에 커밋한다. */
    fun <T : Any> withVerificationTransaction(action: () -> T): T
    fun memberExists(email: String): Boolean
    /** DB collation으로 조회하되 실제 저장된 발송 주소를 반환한다. */
    fun registeredEmail(email: String): String?
    fun sendHistory(email: String, dayStart: Instant): VerificationSendHistory
    fun invalidatePrevious(email: String, purpose: VerificationPurpose, now: Instant)
    fun insert(verification: NewEmailVerification): Long
    fun findById(id: Long): EmailVerification?
    fun findByIdForUpdate(id: Long): EmailVerification?
    fun recordMismatch(id: Long)
    fun replaceUndeliveredCodeHash(id: Long, codeHash: String)
    fun markVerified(id: Long, tokenHash: String, verifiedAt: Instant, tokenExpiresAt: Instant)
}

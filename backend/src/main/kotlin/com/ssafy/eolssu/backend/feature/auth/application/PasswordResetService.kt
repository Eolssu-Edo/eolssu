package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.security.crypto.password.PasswordEncoder

/** 비밀번호 재설정. 이메일 증명으로 재설정하며 로그인/현재 비밀번호 확인 잠금에는 접근하지 않는다. */
class PasswordResetService(
    private val repository: PasswordResetRepository,
    private val passwordEncoder: PasswordEncoder,
    private val secrets: VerificationSecrets,
    private val clock: Clock,
) {
    fun reset(verificationToken: String, newPassword: String): PasswordResetResult {
        if (!PasswordPolicy.isValid(newPassword)) return PasswordResetResult.InvalidRequest
        val tokenHash = secrets.hashToken(verificationToken)
        val proof = repository.findVerification(tokenHash)
        if (!valid(proof, clock.instant())) return PasswordResetResult.EmailNotVerified
        val member = repository.findMember(checkNotNull(proof).email) ?: return PasswordResetResult.EmailNotVerified
        // 과거 입력 변형 주소로 발급된 증명도 DB 등록 주소와 정확히 일치해야 한다.
        if (member.email != proof.email) return PasswordResetResult.EmailNotVerified
        // Argon2는 연결/잠금 밖에서 끝낸다. 이후 변경된 해시에는 이 계산 결과를 적용하지 않는다.
        if (passwordEncoder.matches(newPassword, member.passwordHash)) return PasswordResetResult.InvalidRequest
        val newHash = checkNotNull(passwordEncoder.encode(newPassword))
        return repository.inTransaction {
            val locked = repository.findMemberForUpdate(member.email)
            if (locked == null || locked.id != member.id || locked.email != member.email || locked.passwordHash != member.passwordHash) {
                return@inTransaction PasswordResetResult.EmailNotVerified
            }
            val lockedProof = repository.findVerificationForUpdate(tokenHash)
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (!valid(lockedProof, now) || lockedProof!!.email != locked.email) {
                return@inTransaction PasswordResetResult.EmailNotVerified
            }
            repository.updatePasswordHash(locked.id, newHash)
            repository.markConsumed(lockedProof.id, now)
            repository.revokeAllRefreshTokens(locked.id, now)
            PasswordResetResult.Reset
        }
    }

    private fun valid(proof: ConsumableVerification?, now: Instant): Boolean =
        proof != null && proof.purpose == VerificationPurpose.PASSWORD_RESET && proof.consumedAt == null &&
            proof.invalidatedAt == null && proof.tokenExpiresAt?.isAfter(now) == true
}

sealed interface PasswordResetResult {
    data object Reset : PasswordResetResult
    data object InvalidRequest : PasswordResetResult
    data object EmailNotVerified : PasswordResetResult
}

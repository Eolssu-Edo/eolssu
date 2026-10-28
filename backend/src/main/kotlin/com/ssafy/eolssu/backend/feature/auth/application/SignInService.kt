package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * AUTH-01. 이메일 없음·비밀번호 불일치는 같은 401이다. 탈퇴 대기 회원은 로그인하면 탈퇴가 취소된다.
 * 없는 이메일도 같은 비밀번호 계산을 거쳐 응답 시간으로 가입 여부가 드러나지 않게 한다.
 */
class SignInService(
    private val repository: SignInRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: AuthTokenIssuer,
    private val attempts: LoginAttemptLimiter,
    private val clock: Clock,
) {
    private val absentMemberHash by lazy { checkNotNull(passwordEncoder.encode("absent-member-timing-equalizer")) }

    fun signIn(email: String, password: String): SignInResult {
        val normalized = email.lowercase(Locale.ROOT)
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        // users.email은 악센트·대소문자를 구분하지 않는 콜레이션이라, 입력 문자열로 잠그면 kím@ 같은 변형으로 피할 수 있다.
        // 회원 ID로 잠그면 변형의 잠금 여부가 가입 여부를 드러내므로, 회원과 무관하게 콜레이션 정렬 키로 잠근다.
        val lockKey = repository.lockKey(normalized)
        attempts.lockedUntil(lockKey, now)?.let { return SignInResult.TooManyAttempts(it) }
        val member = repository.findMember(normalized)
        // Argon2 계산(요청당 16MB)은 DB 연결·행 잠금 밖에서 끝낸다. 없는 이메일도 같은 계산을 거친다.
        val passwordMatches = passwordEncoder.matches(password, member?.passwordHash ?: absentMemberHash)
        val result = if (member == null || !passwordMatches) {
            SignInResult.InvalidCredentials
        } else {
            try {
                repository.inTransaction { complete(member, normalized, now) }
            } catch (_: TokenIssuingUnavailable) {
                return SignInResult.Unavailable
            }
        }
        if (result == SignInResult.InvalidCredentials) {
            attempts.recordFailure(lockKey, now)?.let { return SignInResult.TooManyAttempts(it) }
        } else {
            attempts.reset(lockKey)
        }
        return result
    }

    /** 확인한 뒤 비밀번호가 바뀌었거나 다른 회원이 되었으면 로그인하지 않는다. */
    private fun complete(verified: SignInMember, email: String, now: Instant): SignInResult {
        val member = repository.findMemberForUpdate(email)
        if (member == null || member.id != verified.id || member.passwordHash != verified.passwordHash) return SignInResult.InvalidCredentials
        val withdrawalCancelled = member.status == MemberStatus.WITHDRAWAL_PENDING
        if (withdrawalCancelled) repository.cancelWithdrawal(member.id, now)
        repository.recordLogin(member.id, now)
        return SignInResult.SignedIn(member, withdrawalCancelled, tokenIssuer.issue(member.id))
    }
}

sealed interface SignInResult {
    class SignedIn(val member: SignInMember, val withdrawalCancelled: Boolean, val tokens: IssuedTokens) : SignInResult
    data object InvalidCredentials : SignInResult
    data class TooManyAttempts(val retryAvailableAt: Instant) : SignInResult
    data object Unavailable : SignInResult
}

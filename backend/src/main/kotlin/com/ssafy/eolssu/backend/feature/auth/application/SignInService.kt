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
        // 잠금은 DB가 찾은 회원 기준이다. users.email은 악센트·대소문자를 구분하지 않는 콜레이션이라,
        // 입력 문자열 기준이면 kím@처럼 같은 회원을 가리키는 변형으로 잠금을 피할 수 있다.
        val lockKey = repository.findMember(normalized)?.let { "member:${it.id}" } ?: "email:$normalized"
        attempts.lockedUntil(lockKey, now)?.let { return SignInResult.TooManyAttempts(it) }
        val result = try {
            repository.inTransaction { authenticate(normalized, password, now) }
        } catch (_: TokenIssuingUnavailable) {
            return SignInResult.Unavailable
        }
        if (result == SignInResult.InvalidCredentials) {
            attempts.recordFailure(lockKey, now)?.let { return SignInResult.TooManyAttempts(it) }
        } else {
            attempts.reset(lockKey)
        }
        return result
    }

    private fun authenticate(email: String, password: String, now: Instant): SignInResult {
        val member = repository.findMemberForUpdate(email)
        if (member == null) {
            passwordEncoder.matches(password, absentMemberHash)
            return SignInResult.InvalidCredentials
        }
        if (!passwordEncoder.matches(password, member.passwordHash)) return SignInResult.InvalidCredentials
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

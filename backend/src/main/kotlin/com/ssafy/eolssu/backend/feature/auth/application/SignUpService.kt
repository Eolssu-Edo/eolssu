package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.term.domain.TermRepository
import com.ssafy.eolssu.backend.feature.term.domain.Term
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.springframework.security.crypto.password.PasswordEncoder

/** AUTH-02. 인증 완료 토큰 1회 소비·약관·악기 확인 뒤 회원을 만들고 바로 로그인 토큰을 발급한다. 한 트랜잭션이다. */
class SignUpService(
    private val repository: SignUpRepository,
    private val terms: TermRepository,
    private val passwordEncoder: PasswordEncoder,
    private val secrets: VerificationSecrets,
    private val tokenIssuer: AuthTokenIssuer,
    private val clock: Clock,
) {
    fun signUp(command: SignUpCommand): SignUpResult {
        val email = command.email.lowercase(Locale.ROOT)
        if (email.length > 320 || !PasswordPolicy.isValid(command.password)) return SignUpResult.InvalidRequest
        if (command.agreements.map { it.termId }.toSet().size != command.agreements.size) return SignUpResult.InvalidRequest
        // Argon2 계산은 잠금 밖에서 끝낸다.
        val passwordHash = checkNotNull(passwordEncoder.encode(command.password))
        return try {
            repository.inTransaction { create(command, email, passwordHash) }
        } catch (_: EmailAlreadyRegistered) {
            SignUpResult.EmailAlreadyExists
        } catch (_: TokenIssuingUnavailable) {
            SignUpResult.Unavailable
        }
    }

    private fun create(command: SignUpCommand, email: String, passwordHash: String): SignUpResult {
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val verification = repository.findVerificationForUpdate(secrets.hashToken(command.verificationToken))
        if (verification == null || !verification.usableFor(email, now)) return SignUpResult.EmailNotVerified
        if (repository.memberExists(email)) return SignUpResult.EmailAlreadyExists
        val currentTerms = terms.findCurrent(now)
        // An empty/misconfigured catalog must never turn missing consent into successful signup.
        if (currentTerms.none { it.required }) return SignUpResult.Unavailable
        if (!agreesToCurrentTerms(command.agreements, currentTerms)) return SignUpResult.TermsAgreementRequired
        val instrumentId = when (val requested = command.instrumentId) {
            null -> repository.activeInstrumentIdByCode(DEFAULT_INSTRUMENT) ?: return SignUpResult.Unavailable
            else -> requested.takeIf { repository.activeInstrumentExists(it) } ?: return SignUpResult.InvalidRequest
        }
        val notationMode = command.notationMode ?: DEFAULT_NOTATION
        val userId = repository.insertUser(NewUser(email, passwordHash, command.nickname, instrumentId, notationMode), now)
        repository.insertAgreements(userId, command.agreements, now)
        repository.markConsumed(verification.id, now)
        // 서명할 수 없으면 예외로 트랜잭션 전체를 되돌린다.
        val tokens = tokenIssuer.issue(userId)
        return SignUpResult.Created(userId, command.nickname, instrumentId, notationMode, now, tokens)
    }

    private fun ConsumableVerification.usableFor(email: String, now: Instant) =
        purpose == VerificationPurpose.SIGN_UP && this.email == email && consumedAt == null && invalidatedAt == null &&
            tokenExpiresAt?.isAfter(now) == true

    /** 동의 목록은 모두 시행 중인 약관의 현재 버전이어야 하고, 필수 약관은 모두 동의해야 한다. */
    private fun agreesToCurrentTerms(agreements: List<TermAgreement>, currentTerms: List<Term>): Boolean {
        val current = currentTerms.associateBy { it.id }
        if (agreements.any { current[it.termId]?.version != it.version }) return false
        val agreed = agreements.filter { it.agreed }.map { it.termId }.toSet()
        return current.values.filter { it.required }.all { it.id in agreed }
    }

    private companion object {
        const val DEFAULT_INSTRUMENT = "DANSO"
        const val DEFAULT_NOTATION = "HANJA"
    }
}

sealed interface SignUpResult {
    class Created(
        val userId: Long,
        val nickname: String,
        val instrumentId: Long,
        val notationMode: String,
        val createdAt: Instant,
        val tokens: IssuedTokens,
    ) : SignUpResult
    data object InvalidRequest : SignUpResult
    data object EmailNotVerified : SignUpResult
    data object EmailAlreadyExists : SignUpResult
    data object TermsAgreementRequired : SignUpResult
    data object Unavailable : SignUpResult
}

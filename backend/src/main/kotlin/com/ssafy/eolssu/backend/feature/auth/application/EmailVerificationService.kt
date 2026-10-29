package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.EmailVerificationRepository
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationMailSender
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationSecrets
import com.ssafy.eolssu.backend.feature.auth.domain.NewEmailVerification
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationMailUnavailable
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationStorageBusy
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

class EmailVerificationService(
    private val repository: EmailVerificationRepository,
    private val mail: VerificationMailSender,
    private val secrets: VerificationSecrets,
    private val clock: Clock,
) {
    fun confirm(verificationId: Long, code: String): ConfirmVerificationResult {
        if (repository.findById(verificationId) == null) return ConfirmVerificationResult.Missing
        return try {
            repository.withVerificationTransaction {
                val verification = repository.findByIdForUpdate(verificationId) ?: return@withVerificationTransaction ConfirmVerificationResult.Missing
                val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
                if (verification.invalidatedAt != null || verification.verifiedAt != null || !now.isBefore(verification.expiresAt)) {
                    return@withVerificationTransaction ConfirmVerificationResult.Expired
                }
                if (verification.failedAttempts >= 5) return@withVerificationTransaction ConfirmVerificationResult.AttemptsExceeded
                if (!secrets.matchesCode(verification.email, verification.purpose, code, verification.codeHash)) {
                    repository.recordMismatch(verificationId)
                    val remaining = 4 - verification.failedAttempts
                    return@withVerificationTransaction if (remaining == 0) ConfirmVerificationResult.AttemptsExceeded else ConfirmVerificationResult.Mismatch(remaining)
                }
                val token = secrets.generateToken()
                val expiresAt = now.plusSeconds(1800)
                repository.markVerified(verificationId, secrets.hashToken(token), now, expiresAt)
                ConfirmVerificationResult.Verified(token, verification.email, expiresAt)
            }
        } catch (_: VerificationStorageBusy) {
            ConfirmVerificationResult.Unavailable
        } catch (_: VerificationMailUnavailable) {
            // 인증번호 키가 없으면 비교할 수 없다(AUTH_CODE_HMAC_SECRET 미설정).
            ConfirmVerificationResult.Unavailable
        }
    }

    fun send(email: String, purpose: VerificationPurpose): SendVerificationResult {
        val normalized = email.lowercase(Locale.ROOT)
        if (normalized.length > 320) return SendVerificationResult.InvalidEmail
        return try {
            repository.withVerificationTransaction {
                val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
                val memberExists = repository.memberExists(normalized)
                if (purpose == VerificationPurpose.SIGN_UP && memberExists) return@withVerificationTransaction SendVerificationResult.AlreadyRegistered
                // DB가 동일 회원으로 보는 악센트/대소문자 변형을 입력해도, 등록한 주소로만 복구 코드를 보낸다.
                val recipient = if (purpose == VerificationPurpose.PASSWORD_RESET && memberExists) {
                    repository.registeredEmail(normalized) ?: throw VerificationStorageBusy()
                } else normalized
                val day = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
                val dayStart = day.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
                val history = repository.sendHistory(recipient, dayStart)
                if (history.dailyCount >= 10) {
                    return@withVerificationTransaction SendVerificationResult.RateLimited(day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant())
                }
                history.lastSentAt?.plusSeconds(60)?.let { availableAt ->
                    if (now.isBefore(availableAt)) return@withVerificationTransaction SendVerificationResult.RateLimited(availableAt)
                }
                val code = secrets.generateCode()
                val deliver = purpose == VerificationPurpose.SIGN_UP || memberExists
                // 발송하지 않은 요청에는 6자리 입력으로 맞힐 수 없는 임의 256비트 값의 HMAC을 저장한다.
                val codeHash = secrets.hashCode(recipient, purpose, if (deliver) code else secrets.generateToken())
                repository.invalidatePrevious(recipient, purpose, now)
                val id = repository.insert(NewEmailVerification(recipient, purpose, codeHash, now.plusSeconds(180), now))
                // 재설정·계정 연결은 등록 여부를 응답으로 알리지 않는다. 가상 요청도 같은 한도로 기록한다.
                var deliveryFailed = false
                if (deliver) {
                    try {
                        mail.send(recipient, purpose, code)
                    } catch (failure: VerificationMailUnavailable) {
                        // 가입만 발송 장애를 알린다. 재설정·계정 연결은 장애에서도 같은 응답으로 등록 여부를 숨긴다.
                        if (purpose == VerificationPurpose.SIGN_UP) throw failure
                        repository.replaceUndeliveredCodeHash(id, secrets.hashCode(recipient, purpose, secrets.generateToken()))
                        deliveryFailed = true
                    }
                }
                SendVerificationResult.Accepted(id, now.plusSeconds(180), now.plusSeconds(60), deliveryFailed)
            }
        } catch (_: VerificationMailUnavailable) {
            // 트랜잭션 밖에서 변환해 발송 실패 시 이전 번호 무효화·신규 행을 함께 롤백한다.
            SendVerificationResult.Unavailable
        } catch (_: VerificationStorageBusy) {
            SendVerificationResult.Unavailable
        }
    }
}

sealed interface ConfirmVerificationResult {
    class Verified(val verificationToken: String, val email: String, val expiresAt: Instant) : ConfirmVerificationResult
    data object Missing : ConfirmVerificationResult
    data object Expired : ConfirmVerificationResult
    data object AttemptsExceeded : ConfirmVerificationResult
    data class Mismatch(val remainingAttempts: Int) : ConfirmVerificationResult
    data object Unavailable : ConfirmVerificationResult
}

sealed interface SendVerificationResult {
    data class Accepted(val verificationId: Long, val expiresAt: Instant, val resendAvailableAt: Instant, val deliveryFailed: Boolean = false) : SendVerificationResult
    data object AlreadyRegistered : SendVerificationResult
    data object InvalidEmail : SendVerificationResult
    data class RateLimited(val resendAvailableAt: Instant) : SendVerificationResult
    data object Unavailable : SendVerificationResult
}

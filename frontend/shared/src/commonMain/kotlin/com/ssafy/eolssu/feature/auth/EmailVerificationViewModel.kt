package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.ConfirmCodeResult
import com.ssafy.eolssu.data.user.SendCodeResult
import com.ssafy.eolssu.data.user.VerificationPurpose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AUTH-06·AUTH-10. 이메일로 인증번호를 받고 6자리를 확인한다. [purpose]가 가입이면 이미 가입된 이메일을 막고,
 * 비밀번호 찾기면 가입 여부를 숨겨 항상 보낸 것으로 본다(서버 정책). 이메일을 고치면 받은 인증번호는 버린다.
 */
class EmailVerificationViewModel(
    val purpose: VerificationPurpose,
    private val authRepository: AuthRepository,
    private val now: () -> Long,
    private val draft: AccountDraft = AccountDraft(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState: StateFlow<EmailVerificationUiState> = _uiState.asStateFlow()

    fun currentTime(): Long = now()

    private var busy = false
    private var revision = 0
    fun changeEmail(email: String) {
        revision++
        draft.clearVerification()
        _uiState.update { if (it.codeSent) EmailVerificationUiState(email = email) else it.copy(email = email, emailError = null) }
    }

    /** 숫자만 6자리까지 받는다. 새 번호가 필요한 오류는 새로 받을 때까지 남긴다. */
    fun changeCode(code: String) = _uiState.update {
        val keepError = it.codeError == CodeError.Expired || it.codeError == CodeError.TooManyAttempts
        it.copy(code = code.filter { c -> c in '0'..'9' }.take(6), codeError = if (keepError) it.codeError else null)
    }

    /** 인증번호를 보냈으면 true. 이메일이 틀렸거나 이미 가입된 이메일이면 오류를 남긴다. */
    suspend fun sendCode(): Boolean {
        if (busy) return false
        val email = _uiState.value.email.trim()
        AuthValidation.emailError(email)?.let { error ->
            _uiState.update { it.copy(emailError = error) }
            return false
        }
        busy = true
        val requestRevision = revision
        draft.clearVerification()
        val result = try { authRepository.sendCode(email, purpose) } finally { busy = false }
        if (requestRevision != revision) return false
        return when (result) {
            SendCodeResult.Unavailable -> false.also { _uiState.update { it.copy(emailError = FieldError.Unavailable) } }
            is SendCodeResult.RateLimited -> false.also { _uiState.update { it.copy(emailError = FieldError.RateLimited, resendAvailableAtMillis = result.resendAvailableAtMillis ?: it.resendAvailableAtMillis) } }
            SendCodeResult.EmailAlreadyExists -> false.also { _uiState.update { it.copy(emailError = FieldError.EmailAlreadyExists) } }
            is SendCodeResult.Sent -> true.also {
                _uiState.update {
                    it.copy(
                        email = email,
                        code = "",
                        emailError = null,
                        codeError = null,
                        verificationId = result.verificationId,
                        expiresAtMillis = result.expiresAtMillis,
                        resendAvailableAtMillis = result.resendAvailableAtMillis,
                    )
                }
            }
        }
    }

    /** 재발송 간격(60초)이 지났을 때만 다시 보낸다. */
    suspend fun resendCode(): Boolean = if (_uiState.value.resendSecondsLeft(now()) > 0) false else sendCode()

    /** 인증번호가 맞으면 true. 받은 인증 토큰은 가입·재설정 요청에 쓴다. */
    suspend fun confirmCode(): Boolean {
        if (busy) return false
        val state = _uiState.value
        val verificationId = state.verificationId ?: return false
        val current = now()
        if (state.needsNewCode(current)) {
            _uiState.update { it.copy(codeError = state.codeErrorAt(current)) }
            return false
        }
        if (!AuthValidation.isCode(state.code)) {
            _uiState.update { it.copy(codeError = CodeError.Invalid) }
            return false
        }
        busy = true
        val requestRevision = revision
        val result = try { authRepository.confirmCode(verificationId, state.code) } finally { busy = false }
        if (requestRevision != revision) return false
        val error = when (result) {
            is ConfirmCodeResult.Confirmed -> {
                val email = result.email ?: state.email
                if (!email.equals(state.email, ignoreCase = true)) {
                    _uiState.update { it.copy(codeError = CodeError.Unavailable) }; return false
                }
                draft.email = email
                draft.token = result.verificationToken
                draft.purpose = purpose
                _uiState.update { it.copy(email = email, code = "", codeError = null, verificationToken = result.verificationToken) }
                return true
            }
            is ConfirmCodeResult.Mismatch -> CodeError.Mismatch(result.remainingAttempts)
            ConfirmCodeResult.TooManyAttempts -> CodeError.TooManyAttempts
            ConfirmCodeResult.Expired -> CodeError.Expired
            ConfirmCodeResult.Unavailable -> CodeError.Unavailable
        }
        _uiState.update { it.copy(codeError = error) }
        return false
    }
}

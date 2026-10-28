package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AuthRepository
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState: StateFlow<EmailVerificationUiState> = _uiState.asStateFlow()

    fun currentTime(): Long = now()

    fun changeEmail(email: String) = _uiState.update {
        if (it.codeSent) EmailVerificationUiState(email = email) else it.copy(email = email, emailError = null)
    }

    /** 숫자만 6자리까지 받는다. 새 번호가 필요한 오류는 새로 받을 때까지 남긴다. */
    fun changeCode(code: String) = _uiState.update {
        val keepError = it.codeError == CodeError.Expired || it.codeError == CodeError.TooManyAttempts
        it.copy(code = code.filter { c -> c in '0'..'9' }.take(6), codeError = if (keepError) it.codeError else null)
    }

    /** 인증번호를 보냈으면 true. 이메일이 틀렸거나 이미 가입된 이메일이면 오류를 남긴다. */
    fun sendCode(): Boolean {
        val email = _uiState.value.email.trim()
        AuthValidation.emailError(email)?.let { error ->
            _uiState.update { it.copy(emailError = error) }
            return false
        }
        return when (val result = authRepository.sendCode(email, purpose)) {
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
    fun resendCode(): Boolean = if (_uiState.value.resendSecondsLeft(now()) > 0) false else sendCode()

    /** 인증번호가 맞으면 true. 받은 인증 토큰은 가입·재설정 요청에 쓴다. */
    fun confirmCode(): Boolean {
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
        val error = when (val result = authRepository.confirmCode(verificationId, state.code)) {
            is ConfirmCodeResult.Confirmed -> {
                _uiState.update { it.copy(codeError = null, verificationToken = result.verificationToken) }
                return true
            }
            is ConfirmCodeResult.Mismatch -> CodeError.Mismatch(result.remainingAttempts)
            ConfirmCodeResult.TooManyAttempts -> CodeError.TooManyAttempts
            ConfirmCodeResult.Expired -> CodeError.Expired
        }
        _uiState.update { it.copy(codeError = error) }
        return false
    }
}

package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-08·AUTH-11. 프로필 설정(AUTH-07)에서 뒤로 오면 이 화면이 뒤에 남아 있어 입력값이 유지된다. */
class PasswordSetupViewModel(private val auth: AuthRepository? = null, private val draft: AccountDraft = AccountDraft()) : ViewModel() {
    private val _uiState = MutableStateFlow(PasswordSetupUiState())
    val uiState: StateFlow<PasswordSetupUiState> = _uiState.asStateFlow()

    suspend fun submitToServer(purpose: VerificationPurpose, email: String): Boolean {
        if (_uiState.value.submitting || !submit()) return false
        if (draft.purpose != purpose || draft.email != email || draft.token.isBlank()) {
            _uiState.update { it.copy(passwordError = FieldError.VerifyAgain) }
            return false
        }
        val password = _uiState.value.password
        if (purpose == VerificationPurpose.SignUp) { draft.password = password; return true }
        _uiState.update { it.copy(submitting = true) }
        return try {
            val result = auth?.resetPassword(draft.token, password) ?: MemberResult.Unavailable
            if (result == MemberResult.Done) { draft.clearVerification(); _uiState.value = PasswordSetupUiState(); true }
            else { _uiState.update { it.copy(passwordError = result.fieldError()) }; false }
        } finally { _uiState.update { it.copy(submitting = false) } }
    }

    /** 비밀번호를 고치면 확인칸 오류도 다시 판단해야 하므로 함께 지운다. */
    fun changePassword(password: String) =
        _uiState.update { it.copy(password = password, passwordError = null, passwordConfirmError = null) }

    fun changePasswordConfirm(confirm: String) =
        _uiState.update { it.copy(passwordConfirm = confirm, passwordConfirmError = null) }

    /** 규칙에 맞고 확인칸이 같으면 true. 아니면 칸마다 오류를 남긴다. */
    fun submit(): Boolean {
        _uiState.update {
            it.copy(
                passwordError = AuthValidation.passwordError(it.password),
                passwordConfirmError = AuthValidation.passwordConfirmError(it.password, it.passwordConfirm),
            )
        }
        return _uiState.value.let { it.passwordError == null && it.passwordConfirmError == null }
    }
}

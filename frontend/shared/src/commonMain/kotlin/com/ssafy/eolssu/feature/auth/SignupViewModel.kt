package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-06. 프로필 설정(AUTH-07)에서 뒤로 오면 이 화면이 뒤에 남아 있어 입력값이 유지된다. */
class SignupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun changeEmail(email: String) = _uiState.update { it.copy(email = email, emailError = null) }

    /** 비밀번호를 고치면 확인칸 오류도 다시 판단해야 하므로 함께 지운다. */
    fun changePassword(password: String) =
        _uiState.update { it.copy(password = password, passwordError = null, passwordConfirmError = null) }

    fun changePasswordConfirm(confirm: String) =
        _uiState.update { it.copy(passwordConfirm = confirm, passwordConfirmError = null) }

    /** 모든 칸이 맞으면 true. 아니면 칸마다 오류를 남긴다. */
    fun next(): Boolean {
        _uiState.update {
            it.copy(
                emailError = AuthValidation.emailError(it.email),
                passwordError = AuthValidation.passwordError(it.password),
                passwordConfirmError = AuthValidation.passwordConfirmError(it.password, it.passwordConfirm),
            )
        }
        return _uiState.value.let { it.emailError == null && it.passwordError == null && it.passwordConfirmError == null }
    }
}

package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-04. 서버가 없어 [AuthValidation.loginError]만 통과하면 로그인한 것으로 본다. */
class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun changeEmail(email: String) = _uiState.update { it.copy(email = email, error = null) }

    fun changePassword(password: String) = _uiState.update { it.copy(password = password, error = null) }

    /** 로그인에 성공하면 true. 실패하면 오류를 상태에 남긴다. */
    fun logIn(): Boolean {
        val error = _uiState.value.let { AuthValidation.loginError(it.email, it.password) }
        _uiState.update { it.copy(error = error) }
        return error == null
    }
}

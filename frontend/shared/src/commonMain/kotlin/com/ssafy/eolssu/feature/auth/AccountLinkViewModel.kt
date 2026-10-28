package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-09. 계정 연결 API가 생기기 전이라 비밀번호가 필요하면 비어 있는지만 본다. */
class AccountLinkViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AccountLinkUiState())
    val uiState: StateFlow<AccountLinkUiState> = _uiState.asStateFlow()

    fun changePassword(password: String) = _uiState.update { it.copy(password = password, passwordError = null) }

    /** 연결해도 되면 true. [needsPassword]인데 비밀번호가 비어 있으면 오류를 남긴다. */
    fun link(needsPassword: Boolean): Boolean {
        val error = if (needsPassword && _uiState.value.password.isEmpty()) FieldError.PasswordEmpty else null
        _uiState.update { it.copy(passwordError = error) }
        return error == null
    }
}

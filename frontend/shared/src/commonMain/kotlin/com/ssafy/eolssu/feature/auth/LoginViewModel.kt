package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-04. 계정 확인은 [AuthRepository]가 하고, 결과에 따라 오류 문구나 안내 창을 남긴다. */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository? = null,
    private val draft: AccountDraft = AccountDraft(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var loggingIn = false

    fun changeEmail(email: String) = _uiState.update { it.copy(email = email, error = null) }

    fun changePassword(password: String) = _uiState.update { it.copy(password = password, error = null) }

    /**
     * 바로 홈으로 가도 되면 true. 탈퇴 취소는 안내 창을 먼저 띄우고 false를 돌려준다.
     * 서버 응답을 기다리는 동안 다시 누르면 무시하고 false를 돌려준다.
     */
    suspend fun logIn(): Boolean {
        if (loggingIn) return false
        loggingIn = true
        val state = _uiState.value
        return try {
            val result = authRepository.logIn(state.email, state.password)
            when (result) {
            LoginResult.WrongCredentials -> false.also { _uiState.update { it.copy(error = FieldError.LoginFailed) } }
            LoginResult.Unavailable -> false.also { _uiState.update { it.copy(error = FieldError.LoginUnavailable) } }
            is LoginResult.Locked -> false.also {
                _uiState.update { it.copy(error = null, notice = LoginNotice.Locked, lockedUntil = lockRetryTime(result.retryAvailableAtMillis)) }
            }
            is LoginResult.Success -> {
                // A completed login ends the anonymous signup flow, including its consent choices.
                draft.clear()
                userRepository?.logOut()
                userRepository?.refresh(state.email.trim())
                val notice = if (result.withdrawalCancelled) LoginNotice.WithdrawalCancelled else null
                _uiState.update { it.copy(password = "", error = null, notice = notice) }
                notice == null
            }
            }
        } finally { loggingIn = false }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }
}

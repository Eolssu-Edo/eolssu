package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.LoginPreferences
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AUTH-04. 빈 칸·이메일 형식은 보내기 전에 막고, 계정 확인은 [AuthRepository]가 서버로 한다. 결과에 따라 오류 문구나 안내 창을 남긴다.
 * '아이디 기억하기'·'자동 로그인' 선택은 로그인에 성공했을 때 [preferences]에 남긴다.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository? = null,
    private val draft: AccountDraft = AccountDraft(),
    private val preferences: LoginPreferences? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        preferences?.rememberedEmail.let { email ->
            LoginUiState(email = email.orEmpty(), rememberEmail = email != null, keepSignedIn = preferences?.keepSignedIn ?: true)
        },
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun changeEmail(email: String) = _uiState.update { it.copy(email = email, emailError = null, error = null) }

    fun changePassword(password: String) = _uiState.update { it.copy(password = password, passwordError = null, error = null) }

    /** 체크를 풀면 기억한 이메일을 바로 지운다. 켜면 다음 로그인에 성공할 때 남긴다. */
    fun changeRememberEmail(remember: Boolean) {
        _uiState.update { it.copy(rememberEmail = remember) }
        if (!remember) preferences?.forgetEmail()
    }

    fun changeKeepSignedIn(keep: Boolean) = _uiState.update { it.copy(keepSignedIn = keep) }

    /**
     * 바로 홈으로 가도 되면 true. 탈퇴 취소는 안내 창을 먼저 띄우고 false를 돌려준다.
     * 이메일이 비었거나 형식이 틀렸거나 비밀번호가 비었으면 서버에 보내지 않고 칸 아래 오류만 남긴다.
     * 서버 응답을 기다리는 동안 다시 누르면 무시하고 false를 돌려준다.
     */
    suspend fun logIn(): Boolean {
        val state = _uiState.value
        if (state.loggingIn) return false
        val emailError = AuthValidation.emailError(state.email)
        val passwordError = if (state.password.isEmpty()) FieldError.PasswordEmpty else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError, error = null) }
            return false
        }
        _uiState.update { it.copy(loggingIn = true, error = null) }
        return try {
            when (val result = authRepository.logIn(state.email, state.password)) {
                LoginResult.WrongCredentials -> false.also { _uiState.update { it.copy(error = FieldError.LoginFailed) } }
                LoginResult.Unavailable -> false.also { _uiState.update { it.copy(error = FieldError.LoginUnavailable) } }
                is LoginResult.Locked -> false.also {
                    _uiState.update { it.copy(error = null, notice = LoginNotice.Locked, lockedUntil = lockRetryTime(result.retryAvailableAtMillis)) }
                }
                is LoginResult.Success -> {
                    // A completed login ends the anonymous signup flow, including its consent choices.
                    draft.clear()
                    // 회원이 바뀐 뒤에 남겨야 자동 로그인 선택이 회원 변경 정리에 지워지지 않는다.
                    preferences?.saveEmailLogin(state.email, state.rememberEmail, state.keepSignedIn)
                    userRepository?.clearProfile()
                    userRepository?.refresh(state.email.trim())
                    val notice = if (result.withdrawalCancelled) LoginNotice.WithdrawalCancelled else null
                    _uiState.update { it.copy(password = "", error = null, notice = notice) }
                    notice == null
                }
            }
        } finally {
            _uiState.update { it.copy(loggingIn = false) }
        }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }
}

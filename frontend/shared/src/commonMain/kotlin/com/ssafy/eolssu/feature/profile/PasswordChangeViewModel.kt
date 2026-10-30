package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.PasswordRule
import com.ssafy.eolssu.data.user.PasswordCheckResult
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MY-03. 새 비밀번호는 가입과 같은 규칙을 지키고, 현재 비밀번호는 서버가 확인한다. */
class PasswordChangeViewModel(private val userRepository: UserRepository) : ViewModel() {
    private var submitting = false
    private val _uiState = MutableStateFlow(PasswordChangeUiState())
    val uiState: StateFlow<PasswordChangeUiState> = _uiState.asStateFlow()

    fun changeCurrent(value: String) = _uiState.update { it.copy(current = value, currentError = null) }

    /** 새 비밀번호를 고치면 확인칸 오류도 다시 판단해야 하므로 함께 지운다. */
    fun changeNew(value: String) = _uiState.update { it.copy(new = value, newError = null, confirmError = null) }

    fun changeConfirm(value: String) = _uiState.update { it.copy(confirm = value, confirmError = null) }

    /** 바꿨으면 true. */
    suspend fun submit(): Boolean {
        if (submitting) return false
        val state = _uiState.value
        val currentError = if (state.current.isEmpty()) ProfileFieldError.PasswordEmpty else null
        val newError = when {
            state.new.isEmpty() -> ProfileFieldError.PasswordEmpty
            !PasswordRule.isValid(state.new) -> ProfileFieldError.PasswordRule
            else -> null
        }
        val confirmError = if (state.new.isNotEmpty() && state.new != state.confirm) ProfileFieldError.PasswordMismatch else null
        _uiState.update { it.copy(currentError = currentError, newError = newError, confirmError = confirmError) }
        if (currentError != null || newError != null || confirmError != null) return false
        submitting = true
        val result = try { userRepository.changePassword(state.current, state.new) } finally { submitting = false }
        return when (result) {
            PasswordCheckResult.Done -> true.also { _uiState.value = PasswordChangeUiState() }
            PasswordCheckResult.Unavailable -> false.also { _uiState.update { it.copy(currentError = ProfileFieldError.Unavailable) } }
            PasswordCheckResult.Unauthenticated -> false.also { _uiState.update { it.copy(currentError = ProfileFieldError.Unauthenticated) } }
            PasswordCheckResult.RateLimited -> false.also { _uiState.update { it.copy(currentError = ProfileFieldError.RateLimited) } }
            PasswordCheckResult.InvalidInput -> false.also { _uiState.update { it.copy(currentError = ProfileFieldError.InvalidInput) } }
            PasswordCheckResult.WrongPassword -> false.also { _uiState.update { it.copy(currentError = ProfileFieldError.CurrentPasswordWrong) } }
        }
    }
}

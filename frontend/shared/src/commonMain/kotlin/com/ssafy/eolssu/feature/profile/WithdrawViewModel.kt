package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.PasswordCheckResult
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MY-04. 현재 비밀번호로 본인인지 다시 확인한 뒤 탈퇴를 신청한다. */
class WithdrawViewModel(private val userRepository: UserRepository) : ViewModel() {
    private var submitting = false
    private val _uiState = MutableStateFlow(WithdrawUiState())
    val uiState: StateFlow<WithdrawUiState> = _uiState.asStateFlow()

    fun changePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    /** 신청했으면 true. */
    suspend fun submit(): Boolean {
        if (submitting) return false
        val password = _uiState.value.password
        if (password.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFieldError.PasswordEmpty) }
            return false
        }
        submitting = true
        val result = try { userRepository.withdraw(password) } finally { submitting = false }
        return when (result) {
            PasswordCheckResult.Done -> true.also { _uiState.value = WithdrawUiState() }
            PasswordCheckResult.Unavailable -> false.also { _uiState.update { it.copy(error = ProfileFieldError.Unavailable) } }
            PasswordCheckResult.Unauthenticated -> false.also { _uiState.update { it.copy(error = ProfileFieldError.Unauthenticated) } }
            PasswordCheckResult.RateLimited -> false.also { _uiState.update { it.copy(error = ProfileFieldError.RateLimited) } }
            PasswordCheckResult.InvalidInput -> false.also { _uiState.update { it.copy(error = ProfileFieldError.InvalidInput) } }
            PasswordCheckResult.WrongPassword -> false.also { _uiState.update { it.copy(error = ProfileFieldError.PasswordWrong) } }
        }
    }
}

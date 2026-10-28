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
    private val _uiState = MutableStateFlow(WithdrawUiState())
    val uiState: StateFlow<WithdrawUiState> = _uiState.asStateFlow()

    fun changePassword(value: String) = _uiState.update { it.copy(password = value, error = null) }

    /** 신청했으면 true. */
    fun submit(): Boolean {
        val password = _uiState.value.password
        if (password.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFieldError.PasswordEmpty) }
            return false
        }
        return when (userRepository.withdraw(password)) {
            PasswordCheckResult.Done -> true
            PasswordCheckResult.WrongPassword -> false.also { _uiState.update { it.copy(error = ProfileFieldError.PasswordWrong) } }
        }
    }
}

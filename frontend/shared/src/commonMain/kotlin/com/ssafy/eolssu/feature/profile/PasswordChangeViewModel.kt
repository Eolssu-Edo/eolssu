package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.core.network.inAccount
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.PasswordRule
import com.ssafy.eolssu.data.user.PasswordCheckResult
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MY-03. 새 비밀번호는 가입과 같은 규칙을 지키고, 현재 비밀번호는 서버가 확인한다. */
class PasswordChangeViewModel(private val userRepository: UserRepository, member: SignedInMember? = null) : ViewModel() {
    private val _uiState = MutableStateFlow(PasswordChangeUiState())
    val uiState: StateFlow<PasswordChangeUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        _uiState.value = PasswordChangeUiState()
    }

    /** 현재 비밀번호를 고치면 '현재와 같음'도 다시 판단해야 하므로 그 오류를 함께 지운다. */
    fun changeCurrent(value: String) = _uiState.update {
        it.copy(current = value, currentError = null, newError = it.newError.takeUnless { e -> e == ProfileFieldError.PasswordSameAsCurrent })
    }

    /** 새 비밀번호를 고치면 확인칸 오류도 다시 판단해야 하므로 함께 지운다. */
    fun changeNew(value: String) = _uiState.update { it.copy(new = value, newError = null, confirmError = null) }

    fun changeConfirm(value: String) = _uiState.update { it.copy(confirm = value, confirmError = null) }

    /** 바꿨으면 true. */
    suspend fun submit(): Boolean = accountScope.inAccount {
        if (_uiState.value.submitting) return@inAccount false
        val state = _uiState.value
        val currentError = if (state.current.isEmpty()) ProfileFieldError.PasswordEmpty else null
        // 가입(AuthValidation.passwordError)과 같은 순서로 보고, 서버가 거절하는 '현재와 같은 비밀번호'는 보내기 전에 막는다.
        val newError = when {
            state.new.isEmpty() -> ProfileFieldError.PasswordEmpty
            !PasswordRule.meetsAllChecks(state.new) -> ProfileFieldError.PasswordRule
            PasswordRule.isTooLong(state.new) -> ProfileFieldError.PasswordTooLong
            state.new == state.current -> ProfileFieldError.PasswordSameAsCurrent
            else -> null
        }
        val confirmError = if (state.new.isNotEmpty() && state.new != state.confirm) ProfileFieldError.PasswordMismatch else null
        _uiState.update { it.copy(currentError = currentError, newError = newError, confirmError = confirmError, formError = null) }
        if (currentError != null || newError != null || confirmError != null) return@inAccount false
        _uiState.update { it.copy(submitting = true) }
        val result = try { userRepository.changePassword(state.current, state.new) } finally { _uiState.update { it.copy(submitting = false) } }
        return@inAccount when (result) {
            PasswordCheckResult.Done -> true.also { _uiState.value = PasswordChangeUiState() }
            // 칸과 상관없는 실패는 화면 오류로, 서버가 거절한 입력은 새 비밀번호 칸에 붙인다(S15P31E102-996).
            PasswordCheckResult.Unavailable -> false.also { _uiState.update { it.copy(formError = ProfileFieldError.Unavailable) } }
            PasswordCheckResult.Unauthenticated -> false.also { _uiState.update { it.copy(formError = ProfileFieldError.Unauthenticated) } }
            PasswordCheckResult.RateLimited -> false.also { _uiState.update { it.copy(formError = ProfileFieldError.RateLimited) } }
            PasswordCheckResult.InvalidInput -> false.also { _uiState.update { it.copy(newError = ProfileFieldError.InvalidInput) } }
            PasswordCheckResult.WrongPassword -> false.also { _uiState.update { it.copy(currentError = ProfileFieldError.CurrentPasswordWrong) } }
        }
    }
}

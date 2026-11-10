package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.NicknameRule
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.core.network.inAccount
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.MemberResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MY-02. 닉네임은 앞뒤 공백을 뺀 뒤 비어 있지 않고 [NicknameRule.MAX_LENGTH]자 이하여야 한다. */
class NicknameEditViewModel(private val userRepository: UserRepository, member: SignedInMember? = null) : ViewModel() {
    private var submitting = false
    private val _uiState = MutableStateFlow(NicknameEditUiState(nickname = userRepository.profile.value.nickname))
    val uiState: StateFlow<NicknameEditUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        _uiState.value = NicknameEditUiState()
    }

    fun changeNickname(nickname: String) = _uiState.update { it.copy(nickname = nickname, error = null) }

    /** 저장했으면 true. */
    suspend fun save(): Boolean = accountScope.inAccount {
        if (submitting) return@inAccount false
        val nickname = _uiState.value.nickname.trim()
        if (nickname.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFieldError.NicknameEmpty) }
            return@inAccount false
        }
        if (NicknameRule.isTooLong(nickname)) { _uiState.update { it.copy(error = ProfileFieldError.InvalidInput) }; return@inAccount false }
        submitting = true
        val result = try { userRepository.changeNickname(nickname) } finally { submitting = false }
        if (result == MemberResult.Done) return@inAccount true
        _uiState.update { it.copy(error = when (result) {
            MemberResult.Unauthenticated -> ProfileFieldError.Unauthenticated
            MemberResult.RateLimited -> ProfileFieldError.RateLimited
            MemberResult.InvalidInput -> ProfileFieldError.InvalidInput
            else -> ProfileFieldError.Unavailable
        }) }
        return@inAccount false
    }
}

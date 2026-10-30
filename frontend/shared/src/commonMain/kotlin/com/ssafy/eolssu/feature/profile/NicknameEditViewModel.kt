package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.MemberResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MY-02. 닉네임 규칙은 아직 '[규칙 안내]' 자리라 비어 있는지만 본다. */
class NicknameEditViewModel(private val userRepository: UserRepository) : ViewModel() {
    private var submitting = false
    private val _uiState = MutableStateFlow(NicknameEditUiState(nickname = userRepository.profile.value.nickname))
    val uiState: StateFlow<NicknameEditUiState> = _uiState.asStateFlow()

    fun changeNickname(nickname: String) = _uiState.update { it.copy(nickname = nickname, error = null) }

    /** 저장했으면 true. */
    suspend fun save(): Boolean {
        if (submitting) return false
        val nickname = _uiState.value.nickname.trim()
        if (nickname.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFieldError.NicknameEmpty) }
            return false
        }
        if (nickname.length > 60) { _uiState.update { it.copy(error = ProfileFieldError.InvalidInput) }; return false }
        submitting = true
        val result = try { userRepository.changeNickname(nickname) } finally { submitting = false }
        if (result == MemberResult.Done) return true
        _uiState.update { it.copy(error = when (result) {
            MemberResult.Unauthenticated -> ProfileFieldError.Unauthenticated
            MemberResult.RateLimited -> ProfileFieldError.RateLimited
            MemberResult.InvalidInput -> ProfileFieldError.InvalidInput
            else -> ProfileFieldError.Unavailable
        }) }
        return false
    }
}

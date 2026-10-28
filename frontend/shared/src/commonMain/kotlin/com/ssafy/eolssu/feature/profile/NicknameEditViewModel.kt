package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** MY-02. 닉네임 규칙은 아직 '[규칙 안내]' 자리라 비어 있는지만 본다. */
class NicknameEditViewModel(private val userRepository: UserRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NicknameEditUiState(nickname = userRepository.profile.value.nickname))
    val uiState: StateFlow<NicknameEditUiState> = _uiState.asStateFlow()

    fun changeNickname(nickname: String) = _uiState.update { it.copy(nickname = nickname, error = null) }

    /** 저장했으면 true. */
    fun save(): Boolean {
        val nickname = _uiState.value.nickname.trim()
        if (nickname.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFieldError.NicknameEmpty) }
            return false
        }
        userRepository.changeNickname(nickname)
        return true
    }
}

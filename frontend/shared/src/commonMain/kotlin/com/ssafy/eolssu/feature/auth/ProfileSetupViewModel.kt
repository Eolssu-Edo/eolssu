package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-07. 이메일 가입과 소셜 첫 가입이 함께 쓴다. */
class ProfileSetupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    fun changeNickname(nickname: String) = _uiState.update { it.copy(nickname = nickname, nicknameError = null) }

    fun selectInstrument(instrument: ProfileInstrument) = _uiState.update { it.copy(instrument = instrument) }

    /** 닉네임이 있으면 true. 없으면 오류를 남긴다. */
    fun complete(): Boolean {
        val error = AuthValidation.nicknameError(_uiState.value.nickname)
        _uiState.update { it.copy(nicknameError = error) }
        return error == null
    }
}

package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-07 이메일 가입 마지막 단계. 가입을 마치면 고른 악기와 닉네임을 남겨 홈·마이페이지가 같은 값을 쓴다. */
class ProfileSetupViewModel(
    private val instrumentRepository: InstrumentRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    fun changeNickname(nickname: String) = _uiState.update { it.copy(nickname = nickname, nicknameError = null) }

    fun selectInstrument(instrument: Instrument) = _uiState.update { it.copy(instrument = instrument) }

    /** 닉네임이 있으면 악기·닉네임을 남기고 true. 없으면 오류를 남긴다. */
    fun complete(): Boolean {
        val state = _uiState.value
        val error = AuthValidation.nicknameError(state.nickname)
        _uiState.update { it.copy(nicknameError = error) }
        if (error != null) return false
        instrumentRepository.select(state.instrument)
        userRepository.changeNickname(state.nickname.trim())
        return true
    }
}

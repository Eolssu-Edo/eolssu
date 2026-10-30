package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** AUTH-07 이메일 가입 마지막 단계. 서버가 가입을 수락한 뒤 회원 정보를 읽는다. */
class ProfileSetupViewModel(
    private val instrumentRepository: InstrumentRepository,
    private val userRepository: UserRepository,
    private val auth: AuthRepository,
    private val draft: AccountDraft,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    fun changeNickname(nickname: String) = _uiState.update { it.copy(nickname = nickname, nicknameError = null) }

    fun selectInstrument(instrument: Instrument) = _uiState.update { it.copy(instrument = instrument) }

    private var submitting = false

    /** 인증 토큰·실제 동의와 입력을 보내고 서버가 계정을 생성했을 때만 true. */
    suspend fun complete(): Boolean {
        if (submitting) return false
        val state = _uiState.value
        val error = AuthValidation.nicknameError(state.nickname)
        _uiState.update { it.copy(nicknameError = error) }
        if (error != null) return false
        if (draft.token.isBlank() || draft.password.isBlank() || draft.purpose != VerificationPurpose.SignUp) {
            _uiState.update { it.copy(nicknameError = FieldError.VerifyAgain) }; return false
        }
        if (draft.agreements.isEmpty()) {
            _uiState.update { it.copy(nicknameError = FieldError.TermsAgain) }; return false
        }
        submitting = true
        return try {
            val instrumentId = instrumentRepository.serverIdFor(state.instrument)
            if (instrumentId == null) {
                _uiState.update { it.copy(nicknameError = FieldError.InstrumentUnavailable) }
                return false
            }
            val result = auth.signUp(Registration(draft.email, draft.password, state.nickname.trim(), draft.token, draft.agreements, instrumentId))
            if (result == MemberResult.Done) {
                instrumentRepository.select(state.instrument)
                userRepository.logOut()
                userRepository.refresh(draft.email)
                draft.clear()
                true
            } else {
                if (result == MemberResult.TermsRequired) draft.agreements = emptyList()
                _uiState.update { it.copy(nicknameError = result.fieldError()) }; false
            }
        } finally { submitting = false }
    }
}

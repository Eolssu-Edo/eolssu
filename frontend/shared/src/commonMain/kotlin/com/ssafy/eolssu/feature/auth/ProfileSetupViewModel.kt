package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.NicknameRule
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.data.user.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AUTH-07 가입 마지막 단계. 이메일 가입은 인증 토큰·비밀번호로, [social] 가입은 간편 로그인이 남긴 가입 티켓([OAuthAttempts.signUpTicket])으로 보낸다.
 * 서버가 가입을 수락한 뒤 회원 정보를 읽는다. 소셜 가입 티켓은 1회용이라 만료·재사용이면 간편 로그인부터 다시 하게 한다.
 */
class ProfileSetupViewModel(
    private val instrumentRepository: InstrumentRepository,
    private val userRepository: UserRepository,
    private val auth: AuthRepository,
    private val draft: AccountDraft,
    private val attempts: OAuthAttempts? = null,
    val social: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    /** 서버 길이([NicknameRule.MAX_LENGTH]자)를 넘는 입력·붙여넣기는 칸에 남기지 않는다. */
    fun changeNickname(nickname: String) = _uiState.update {
        it.copy(nickname = NicknameRule.limit(it.nickname, nickname), nicknameError = null)
    }

    fun selectInstrument(instrument: Instrument) = _uiState.update { it.copy(instrument = instrument) }

    /** 실패를 닉네임 칸 오류와 화면 오류로 나눈다. 닉네임 칸에는 닉네임 때문에 생긴 오류만 붙인다. */
    private fun fail(error: FieldError): Boolean {
        val nickname = error == FieldError.NicknameEmpty || error == FieldError.InvalidInput
        _uiState.update { it.copy(nicknameError = error.takeIf { nickname }, formError = error.takeUnless { nickname }) }
        return false
    }

    /** 인증 토큰(소셜은 가입 티켓)·실제 동의와 입력을 보내고 서버가 계정을 생성했을 때만 true. */
    suspend fun complete(): Boolean {
        if (_uiState.value.submitting) return false
        val state = _uiState.value
        _uiState.update { it.copy(formError = null) }
        AuthValidation.nicknameError(state.nickname)?.let { return fail(it) }
        _uiState.update { it.copy(nicknameError = null) }
        val ticket = if (social) {
            attempts?.signUpTicket() ?: return fail(FieldError.SignUpExpired)
        } else {
            if (draft.token.isBlank() || draft.password.isBlank() || draft.purpose != VerificationPurpose.SignUp) {
                return fail(FieldError.VerifyAgain)
            }
            null
        }
        if (draft.agreements.isEmpty()) return fail(FieldError.TermsAgain)
        _uiState.update { it.copy(submitting = true) }
        return try {
            val instrumentId = instrumentRepository.serverIdFor(state.instrument)
                ?: return fail(FieldError.InstrumentUnavailable)
            val result = if (ticket != null) {
                auth.socialSignUp(SocialRegistration(ticket, state.nickname.trim(), draft.agreements, instrumentId))
            } else {
                auth.signUp(Registration(draft.email, draft.password, state.nickname.trim(), draft.token, draft.agreements, instrumentId))
            }
            if (result == MemberResult.Done) {
                instrumentRepository.select(state.instrument)
                userRepository.clearProfile()
                userRepository.refresh(draft.email.takeIf { ticket == null })
                draft.clear()
                attempts?.clearSignUp()
                true
            } else {
                if (result == MemberResult.TermsRequired) draft.agreements = emptyList()
                // 티켓이 만료·재사용됐거나 이미 다른 회원이 됐으면 같은 티켓으로 다시 보내도 같다.
                if (result == MemberResult.TicketExpired || result == MemberResult.SocialAccountInUse) attempts?.clearSignUp()
                fail(result.fieldError(social))
            }
        } finally { _uiState.update { it.copy(submitting = false) } }
    }
}

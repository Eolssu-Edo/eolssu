package com.ssafy.eolssu.feature.auth

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.AccountDraft
import com.ssafy.eolssu.data.user.AuthRepository
import com.ssafy.eolssu.data.user.MergeResult
import com.ssafy.eolssu.data.user.OAuthAttempts
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AUTH-09. 간편 로그인이 남긴 계정 연결 제안([OAuthAttempts.mergeOffer])으로 `POST /api/auth/oauth/merge`를 보낸다.
 * 제공자가 이메일을 확인해 줬으면 동의만, 아니면 얼쑤 비밀번호를 받는다. 비밀번호를 틀려도 제안(10분·1회)은 남아 다시 입력할 수 있다.
 */
class AccountLinkViewModel(
    private val attempts: OAuthAttempts,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository? = null,
    private val draft: AccountDraft = AccountDraft(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        attempts.mergeOffer()?.let { AccountLinkUiState(it.provider, it.maskedEmail, it.needsPassword) } ?: AccountLinkUiState(expired = true),
    )
    val uiState: StateFlow<AccountLinkUiState> = _uiState.asStateFlow()

    fun changePassword(password: String) = _uiState.update { it.copy(password = password, passwordError = null, error = null) }

    /** 연결하고 로그인했으면 true(홈으로). 비밀번호가 필요한데 비어 있거나, 요청 중이거나, 실패하면 false. */
    suspend fun link(): Boolean {
        val state = _uiState.value
        if (state.linking || state.expired) return false
        if (state.needsPassword && state.password.isEmpty()) {
            _uiState.update { it.copy(passwordError = FieldError.PasswordEmpty) }
            return false
        }
        val offer = attempts.mergeOffer() ?: return false.also { _uiState.update { it.copy(expired = true) } }
        _uiState.update { it.copy(linking = true, passwordError = null, error = null) }
        // 요청이 예외로 끝나도 버튼이 진행 중으로 남지 않게 한다(S15P31E102-996).
        val result = try {
            authRepository.mergeAccount(offer, state.password.takeIf { state.needsPassword })
        } finally {
            _uiState.update { it.copy(linking = false) }
        }
        when (result) {
            MergeResult.Success -> {
                attempts.clearMerge()
                draft.clear()
                userRepository?.clearProfile()
                userRepository?.refresh()
                _uiState.update { it.copy(password = "") }
                return true
            }
            // 서버가 본인 확인을 요구하면 비밀번호 칸을 연다.
            MergeResult.PasswordRequired -> _uiState.update { it.copy(needsPassword = true, passwordError = FieldError.PasswordEmpty) }
            MergeResult.WrongPassword -> _uiState.update { it.copy(passwordError = FieldError.MergeWrongPassword) }
            MergeResult.Locked -> _uiState.update { it.copy(error = FieldError.MergeLocked) }
            MergeResult.Expired -> {
                attempts.clearMerge()
                _uiState.update { it.copy(expired = true) }
            }
            MergeResult.Unavailable -> _uiState.update { it.copy(error = FieldError.Unavailable) }
        }
        return false
    }

    /** '연결하지 않기'·뒤로 가기. 남은 제안을 버린다. */
    fun cancel() = attempts.clearMerge()
}

package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.InputLimit
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
    private val _uiState = MutableStateFlow(NicknameEditUiState(nickname = userRepository.profile.value.nickname))
    val uiState: StateFlow<NicknameEditUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) { clearAccount() }
    private fun clearAccount() {
        _uiState.value = NicknameEditUiState()
    }

    /**
     * 가입(AUTH)과 달리 넘치는 입력도 칸에 남겨 글자 수(n/10)와 함께 '10자 이내' 오류를 바로 보인다.
     * 다시 10자 안으로 줄이면 오류가 사라진다. 다만 붙여넣기로 끝없이 길어지지 않게 [OverflowLimit]자에서 더 받지 않는다(S15P31E102-996).
     */
    fun changeNickname(input: String) = _uiState.update {
        val nickname = InputLimit.limit(it.nickname, input, OverflowLimit)
        it.copy(nickname = nickname, error = if (NicknameRule.isTooLong(nickname)) ProfileFieldError.NicknameTooLong else null)
    }

    /** 저장했으면 true. */
    suspend fun save(): Boolean = accountScope.inAccount {
        if (_uiState.value.submitting) return@inAccount false
        val nickname = _uiState.value.nickname.trim()
        if (nickname.isEmpty()) {
            _uiState.update { it.copy(error = ProfileFieldError.NicknameEmpty) }
            return@inAccount false
        }
        if (NicknameRule.isTooLong(nickname)) { _uiState.update { it.copy(error = ProfileFieldError.NicknameTooLong) }; return@inAccount false }
        // 지금 닉네임과 같으면 요청 없이 저장한 것으로 본다(S15P31E102-996).
        if (nickname == userRepository.profile.value.nickname) return@inAccount true
        _uiState.update { it.copy(submitting = true) }
        val result = try { userRepository.changeNickname(nickname) } finally { _uiState.update { it.copy(submitting = false) } }
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

/** 닉네임 수정 칸이 받는 최대 글자 수. 상한(10자)을 넘긴 것을 보여 줄 만큼만 더 받는다. */
private const val OverflowLimit = NicknameRule.MAX_LENGTH + 10

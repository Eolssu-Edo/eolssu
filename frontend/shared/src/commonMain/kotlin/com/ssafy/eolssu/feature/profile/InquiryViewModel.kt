package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inAccount
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 마이페이지 문의하기(S15P31E102-1240). 제목은 한 줄, 내용은 여러 줄이며 앞뒤 공백을 뺀 뒤 비어 있으면 보내지 않는다.
 * 제목의 줄바꿈은 서버가 거절하므로 칸에서 공백으로 바꾼다.
 */
class InquiryViewModel(private val userRepository: UserRepository, member: SignedInMember? = null) : ViewModel() {
    private val _uiState = MutableStateFlow(InquiryUiState())
    val uiState: StateFlow<InquiryUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) { _uiState.value = InquiryUiState() }

    fun changeTitle(input: String) = _uiState.update {
        it.copy(title = input.replace(LINE_BREAK, " "), titleError = null, error = null)
    }

    fun changeContent(input: String) = _uiState.update { it.copy(content = input, error = null) }

    /** 보냈으면 true. */
    suspend fun send(): Boolean = accountScope.inAccount {
        val state = _uiState.value
        if (state.submitting) return@inAccount false
        val title = state.title.trim()
        val content = state.content.trim()
        val titleError = if (title.isEmpty()) ProfileFieldError.InquiryTitleEmpty else null
        val contentError = if (content.isEmpty()) ProfileFieldError.InquiryContentEmpty else null
        if (titleError != null || contentError != null) {
            _uiState.update { it.copy(titleError = titleError, error = contentError) }
            return@inAccount false
        }
        _uiState.update { it.copy(submitting = true) }
        val result = try { userRepository.sendInquiry(title, content) } finally { _uiState.update { it.copy(submitting = false) } }
        if (result == MemberResult.Done) return@inAccount true
        _uiState.update {
            it.copy(error = when (result) {
                MemberResult.Unauthenticated -> ProfileFieldError.Unauthenticated
                MemberResult.RateLimited -> ProfileFieldError.RateLimited
                MemberResult.InvalidInput -> ProfileFieldError.InvalidInput
                else -> ProfileFieldError.InquiryUnavailable
            })
        }
        return@inAccount false
    }

    private companion object {
        val LINE_BREAK = Regex("[\\r\\n]+")
    }
}

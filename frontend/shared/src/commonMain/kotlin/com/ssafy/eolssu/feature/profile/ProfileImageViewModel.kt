package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inAccount
import com.ssafy.eolssu.core.network.screenScope
import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.data.user.ProfileImage
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 프로필 그림 저장 결과. 고른 그림이 지금과 같으면 요청 없이 [Unchanged]다. */
enum class ProfileImageSave { Saved, Unchanged, Failed }

/** 마이페이지 프로필 그림 고르기(S15P31E102-738). 처음에는 지금 그림이 골라져 있다. */
class ProfileImageViewModel(private val userRepository: UserRepository, member: SignedInMember? = null) : ViewModel() {
    private var submitting = false
    private val _uiState = MutableStateFlow(ProfileImageUiState(selected = userRepository.profile.value.profileImage))
    val uiState: StateFlow<ProfileImageUiState> = _uiState.asStateFlow()

    private val accountScope = member.screenScope(viewModelScope) { _uiState.value = ProfileImageUiState() }

    fun select(image: ProfileImage) = _uiState.update { it.copy(selected = image, error = null) }

    suspend fun save(): ProfileImageSave = accountScope.inAccount {
        if (submitting) return@inAccount ProfileImageSave.Failed
        val image = _uiState.value.selected
        if (image == userRepository.profile.value.profileImage) return@inAccount ProfileImageSave.Unchanged
        submitting = true
        val result = try { userRepository.changeProfileImage(image) } finally { submitting = false }
        if (result == MemberResult.Done) return@inAccount ProfileImageSave.Saved
        _uiState.update { it.copy(error = when (result) {
            MemberResult.Unauthenticated -> ProfileFieldError.Unauthenticated
            MemberResult.RateLimited -> ProfileFieldError.RateLimited
            else -> ProfileFieldError.Unavailable
        }) }
        return@inAccount ProfileImageSave.Failed
    }
}

data class ProfileImageUiState(
    val selected: ProfileImage = ProfileImage.Hello,
    val error: ProfileFieldError? = null,
)

package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 내 정보(`GET /api/users/me`). [linkedProviders]는 응답의 `linkedProviders`다. */
data class UserProfile(
    val nickname: String,
    val email: String,
    val linkedProviders: Set<SocialProvider>,
)

sealed interface PasswordCheckResult {
    data object Done : PasswordCheckResult

    /** 현재 비밀번호가 맞지 않는다. */
    data object WrongPassword : PasswordCheckResult
}

/**
 * 마이페이지의 내 정보·간편 로그인 연결·비밀번호 변경·로그아웃·탈퇴.
 * 회원 정보·연결 API가 붙기 전이라 지금은 [SampleUserRepository]가 예시 계정을 기기 메모리에 둔다.
 * 간편 로그인 연결은 제공자 로그인을 거친 뒤 서버가 확정하므로 [link]는 그 결과만 반영한다.
 */
interface UserRepository {
    val profile: StateFlow<UserProfile>

    fun changeNickname(nickname: String)

    fun link(provider: SocialProvider)

    /** 가입은 이메일로만 하므로 이메일 로그인이 항상 남아 있어, 마지막 간편 로그인도 해제할 수 있다. */
    fun unlink(provider: SocialProvider)

    fun changePassword(current: String, new: String): PasswordCheckResult

    fun logOut()

    /** 탈퇴를 신청한다. 30일 뒤 삭제되고, 그 전에 로그인하면 취소된다. */
    fun withdraw(password: String): PasswordCheckResult
}

/** 예시 계정(얼쑤연주자 · 카카오 연결). 비밀번호는 비어 있지 않으면 맞는 것으로 본다. */
class SampleUserRepository : UserRepository {
    private val _profile = MutableStateFlow(UserProfile("얼쑤연주자", "user@example.com", setOf(SocialProvider.Kakao)))
    override val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    override fun changeNickname(nickname: String) = _profile.update { it.copy(nickname = nickname) }

    override fun link(provider: SocialProvider) = _profile.update { it.copy(linkedProviders = it.linkedProviders + provider) }

    override fun unlink(provider: SocialProvider) = _profile.update { it.copy(linkedProviders = it.linkedProviders - provider) }

    override fun changePassword(current: String, new: String): PasswordCheckResult = check(current)

    override fun logOut() = Unit

    override fun withdraw(password: String): PasswordCheckResult = check(password)

    private fun check(password: String) = if (password.isEmpty()) PasswordCheckResult.WrongPassword else PasswordCheckResult.Done
}

package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.ProfileImage
import com.ssafy.eolssu.core.model.SocialProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 내 정보(`GET /api/users/me`). [linkedProviders]는 응답의 `linkedProviders`다. 이메일이 없는 소셜 가입 회원은 [email]이 빈 문자열이다.
 * [hasPassword]는 서버가 비밀번호 유무를 알려 줄 때의 값이고, 아직 알려 주지 않으면 null이다([signsInWithPassword] 참고).
 */
data class UserProfile(
    val nickname: String,
    val email: String,
    val linkedProviders: Set<SocialProvider>,
    val instrumentId: String? = null,
    val notationMode: String? = null,
    val hasPassword: Boolean? = null,
    val profileImage: ProfileImage = ProfileImage.Hello,
)

/**
 * 이메일·비밀번호로 로그인하는 회원인지. 서버가 `hasPassword`를 주면 그 값을 쓰고, 주지 않으면 이메일이 있는지로 짐작한다.
 * 짐작은 제공자가 확인한 이메일로 소셜 가입한 회원(비밀번호 없음)을 비밀번호 회원으로 잘못 볼 수 있다.
 */
val UserProfile.signsInWithPassword: Boolean get() = hasPassword ?: email.isNotBlank()

sealed interface PasswordCheckResult {
    data object Unavailable : PasswordCheckResult
    data object Unauthenticated : PasswordCheckResult
    data object RateLimited : PasswordCheckResult
    data object InvalidInput : PasswordCheckResult
    data object Done : PasswordCheckResult

    /** 현재 비밀번호가 맞지 않는다. */
    data object WrongPassword : PasswordCheckResult
}

/**
 * 마이페이지의 내 정보·간편 로그인 연결·비밀번호 변경·로그아웃·탈퇴.
 * 출시에서는 [ApiUserRepository]가 서버 응답을 확인한다. 예시 구현은 격리된 테스트에만 쓴다.
 * 간편 로그인 연결은 [startLink]로 받은 제공자 화면을 거친 뒤 [link]로 서버가 확정하고, 성공하면 [profile]의 연결 목록에 반영한다.
 */
interface UserRepository {
    suspend fun refresh(email: String? = null): MemberResult = MemberResult.Done

    val profile: StateFlow<UserProfile>

    suspend fun changeNickname(nickname: String): MemberResult

    suspend fun changeInstrument(instrumentId: String): MemberResult = MemberResult.Unavailable

    /** 프로필 그림을 바꾼다. 서버가 받아들인 그림만 [profile]에 반영한다. */
    suspend fun changeProfileImage(image: ProfileImage): MemberResult = MemberResult.Unavailable

    /** 마이페이지 연결 start. 받은 제공자 주소를 외부 브라우저로 연다. */
    suspend fun startLink(provider: SocialProvider): OAuthStartResult = OAuthStartResult.Unavailable

    /** 복귀 주소의 코드·state로 연결을 끝낸다. state가 1회용이라 실패해도 같은 [callback]으로 다시 부르지 않는다. */
    suspend fun link(callback: OAuthCallback): LinkResult

    /**
     * 연결을 해제한다. 이미 해제돼 있었으면(404) 결과가 같아 [MemberResult.Done]이다.
     * 비밀번호 없는 소셜 가입 회원의 마지막 간편 로그인은 서버가 거절한다([MemberResult.LastLoginMethod]).
     */
    suspend fun unlink(provider: SocialProvider): MemberResult

    suspend fun changePassword(current: String, new: String): PasswordCheckResult

    /** 표시 중인 프로필만 비운다. 정상 로그인 뒤 호출해도 로그인 회원·세대는 유지한다. */
    fun clearProfile()

    /** 세션 만료·실제 로그아웃에서 로그인 회원과 프로필을 함께 비운다. */
    fun logOut()

    /** 탈퇴를 신청한다. 30일 뒤 삭제되고, 그 전에 로그인하면 취소된다. */
    suspend fun withdraw(password: String): PasswordCheckResult

    /** 탈퇴 재인증 start. 받은 제공자 주소를 외부 브라우저로 연다. */
    suspend fun startWithdrawalReauth(provider: SocialProvider): OAuthStartResult = OAuthStartResult.Unavailable

    /** 복귀 주소의 코드·state로 재인증을 끝내고 탈퇴 증명을 받는다. state가 1회용이라 실패해도 같은 [callback]으로 다시 부르지 않는다. */
    suspend fun confirmWithdrawalReauth(callback: OAuthCallback): ReauthResult = ReauthResult.Unavailable

    /** 간편 로그인 재인증 증명으로 탈퇴를 신청한다. 증명이 만료·재사용이면 [MemberResult.TicketExpired]다. */
    suspend fun withdraw(proof: WithdrawalProof): MemberResult = MemberResult.Unavailable
}

/** 예시 계정(얼쑤연주자 · 카카오 연결). 비밀번호는 비어 있지 않으면 맞는 것으로 본다. */
class SampleUserRepository : UserRepository {
    private val _profile = MutableStateFlow(UserProfile("얼쑤연주자", "user@example.com", setOf(SocialProvider.Kakao)))
    override val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    override suspend fun changeNickname(nickname: String): MemberResult { _profile.update { it.copy(nickname = nickname) }; return MemberResult.Done }

    override suspend fun changeProfileImage(image: ProfileImage): MemberResult { _profile.update { it.copy(profileImage = image) }; return MemberResult.Done }

    override suspend fun link(callback: OAuthCallback): LinkResult {
        _profile.update { it.copy(linkedProviders = it.linkedProviders + callback.provider) }
        return LinkResult.Linked
    }

    override suspend fun unlink(provider: SocialProvider): MemberResult {
        _profile.update { it.copy(linkedProviders = it.linkedProviders - provider) }
        return MemberResult.Done
    }

    override suspend fun changePassword(current: String, new: String): PasswordCheckResult = check(current)

    override fun logOut() = Unit

    override fun clearProfile() = Unit

    override suspend fun withdraw(password: String): PasswordCheckResult = check(password)

    private fun check(password: String) = if (password.isEmpty()) PasswordCheckResult.WrongPassword else PasswordCheckResult.Done
}

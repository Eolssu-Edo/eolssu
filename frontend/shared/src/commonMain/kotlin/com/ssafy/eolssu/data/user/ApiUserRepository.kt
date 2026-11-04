package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable

/** /me의 사용자 식별자는 서버 쿠키에서 결정한다. 이메일은 로그인한 세션의 메모리에만 둔다. */
class ApiUserRepository(
    private val client: HttpClient,
    private val cookies: SecureCookiesStorage?,
    private val instruments: InstrumentRepository? = null,
    private val member: com.ssafy.eolssu.core.network.SignedInMember? = null,
    private val route: OAuthRoute = OAuthRedirect,
) : UserRepository {
    private val state = MutableStateFlow(UserProfile("", "", emptySet()))
    override val profile = state.asStateFlow()
    private val sessionGeneration = MutableStateFlow(0L)
    private val profileRevision = MutableStateFlow(0L)

    init { member?.onChange { clearProfile() } }

    override suspend fun refresh(email: String?): MemberResult = member.inSession {
        val generation = sessionGeneration.value
        val revision = profileRevision.value
        val response = client.sendOrNull { get("/api/users/me") } ?: return@inSession MemberResult.Unavailable
        if (response.status.value != 200) return@inSession memberFailure(response.status.value)
        val result = response.apiResponse<MyInfo>()?.takeIf { it.isSuccess }?.result ?: return@inSession MemberResult.Unavailable
        if (sessionGeneration.value != generation) return@inSession MemberResult.Unauthenticated
        if (profileRevision.value != revision) return@inSession MemberResult.Unavailable
        state.value = UserProfile(result.nickname, result.email ?: email ?: state.value.email, result.linkedProviders.mapNotNull { provider ->
            SocialProvider.entries.firstOrNull { it.name.equals(provider.provider, ignoreCase = true) }
        }.toSet(), result.instrumentId, result.notationMode, result.hasPassword)
        if (instruments != null && !instruments.applyServerId(result.instrumentId)) return@inSession MemberResult.Unavailable
        return@inSession MemberResult.Done
    }

    override suspend fun changeInstrument(instrumentId: String): MemberResult = member.inSession {
        val generation = sessionGeneration.value
        if (!instrumentId.matches(Regex("[1-9][0-9]{0,17}"))) return@inSession MemberResult.InvalidInput
        val response = client.sendOrNull {
            patch("/api/users/me/preferences") { contentType(ContentType.Application.Json); setBody(InstrumentPreference(instrumentId)) }
        } ?: return@inSession MemberResult.Unavailable
        if (response.status.value != 200) return@inSession memberFailure(response.status.value)
        val saved = response.apiResponse<Preferences>()?.takeIf { it.isSuccess }?.result ?: return@inSession MemberResult.Unavailable
        if (sessionGeneration.value != generation) return@inSession MemberResult.Unauthenticated
        if (saved.instrumentId != instrumentId) return@inSession MemberResult.Unavailable
        profileRevision.updateAndGet { it + 1 }
        state.value = state.value.copy(instrumentId = saved.instrumentId, notationMode = saved.notationMode)
        return@inSession MemberResult.Done
    }

    override suspend fun changeNickname(nickname: String): MemberResult = member.inSession {
        val response = client.sendOrNull {
            patch("/api/users/me") { contentType(ContentType.Application.Json); setBody(Nickname(nickname)) }
        } ?: return@inSession MemberResult.Unavailable
        if (response.status.value != 200) return@inSession memberFailure(response.status.value)
        val result = response.apiResponse<Nickname>()?.takeIf { it.isSuccess }?.result ?: return@inSession MemberResult.Unavailable
        profileRevision.updateAndGet { it + 1 }
        state.value = state.value.copy(nickname = result.nickname)
        return@inSession MemberResult.Done
    }

    override suspend fun changePassword(current: String, new: String): PasswordCheckResult = member.inSession {
        val response = client.sendOrNull {
            patch("/api/users/me/password") { contentType(ContentType.Application.Json); setBody(PasswordChange(current, new)) }
        } ?: return@inSession PasswordCheckResult.Unavailable
        if (response.status.value == 200 && response.apiResponse<PasswordChanged>()?.takeIf { it.isSuccess }?.result?.changed == true) return@inSession PasswordCheckResult.Done
        return@inSession passwordFailure(response.status.value, response.apiResponse<Error>()?.result?.errorCode)
    }

    override suspend fun withdraw(password: String): PasswordCheckResult {
        return member?.changingCurrent { withdrawCurrentMember(password) } ?: withdrawCurrentMember(password)
    }

    private suspend fun withdrawCurrentMember(password: String): PasswordCheckResult {
        val response = client.sendOrNull {
            delete("/api/users/me") { contentType(ContentType.Application.Json); setBody(CurrentPassword(password)) }
        } ?: return PasswordCheckResult.Unavailable
        if (response.status.value == 200 && response.apiResponse<Withdrawn>()?.takeIf { it.isSuccess }?.result?.withdrawn == true) {
            cookies?.clear()
            member?.set(null)
            logOut()
            return PasswordCheckResult.Done
        }
        return passwordFailure(response.status.value, response.apiResponse<Error>()?.result?.errorCode)
    }

    override suspend fun startWithdrawalReauth(provider: SocialProvider): OAuthStartResult =
        member.inSession { startOAuth(client, "/api/users/me/withdrawal/oauth/${provider.pathName}/start", route.uriFor(provider)) }

    /** 연결 완료([link])처럼 먼저 갱신되는 요청으로 로그인을 이어 둔 뒤, 갱신·재전송하지 않는 완료 요청을 보낸다. */
    override suspend fun confirmWithdrawalReauth(callback: OAuthCallback): ReauthResult = member.inSession {
        val me = client.sendOrNull { get("/api/users/me") } ?: return@inSession ReauthResult.Unavailable
        if (me.status.value == 401) return@inSession ReauthResult.Unauthenticated
        val response = client.sendOrNull {
            post("/api/users/me/withdrawal/oauth/${callback.provider.pathName}") {
                contentType(ContentType.Application.Json)
                setBody(OAuthComplete(callback.authorizationCode, callback.redirectUri, callback.state))
            }
        } ?: return@inSession ReauthResult.Unavailable
        if (response.status.value == 200) {
            val ticket = response.apiResponse<WithdrawalTicket>()?.takeIf { it.isSuccess }?.result?.withdrawalTicket
                ?.takeIf { it.isNotBlank() } ?: return@inSession ReauthResult.Unavailable
            return@inSession ReauthResult.Proven(WithdrawalProof(callback.provider, ticket))
        }
        val code = response.apiResponse<Error>()?.result?.errorCode
        return@inSession when {
            response.status.value == 401 -> if (code == "UNAUTHENTICATED") ReauthResult.Unauthenticated else ReauthResult.Restart
            response.status.value == 400 || response.status.value == 404 || response.status.value == 410 -> ReauthResult.Restart
            else -> ReauthResult.Unavailable
        }
    }

    override suspend fun withdraw(proof: WithdrawalProof): MemberResult {
        return member?.changingCurrent { withdrawWithProof(proof) } ?: withdrawWithProof(proof)
    }

    private suspend fun withdrawWithProof(proof: WithdrawalProof): MemberResult {
        val response = client.sendOrNull {
            delete("/api/users/me") { contentType(ContentType.Application.Json); setBody(WithdrawalTicketBody(proof.ticket)) }
        } ?: return MemberResult.Unavailable
        if (response.status.value == 200 && response.apiResponse<Withdrawn>()?.takeIf { it.isSuccess }?.result?.withdrawn == true) {
            cookies?.clear()
            member?.set(null)
            logOut()
            return MemberResult.Done
        }
        return memberFailure(response.status.value, response.apiResponse<Error>()?.result?.errorCode)
    }

    override suspend fun startLink(provider: SocialProvider): OAuthStartResult =
        member.inSession { startOAuth(client, "/api/users/me/social-accounts/${provider.pathName}/start", route.uriFor(provider)) }

    /**
     * 연결 완료는 state를 한 번 쓰므로 401에 토큰 갱신·재전송을 하지 않는다. 브라우저에 다녀오는 동안 액세스 토큰이 만료됐을 수 있어
     * 먼저 갱신되는 요청(`GET /api/users/me`)으로 로그인을 이어 둔 뒤 보낸다.
     */
    override suspend fun link(callback: OAuthCallback): LinkResult = member.inSession {
        val generation = sessionGeneration.value
        val me = client.sendOrNull { get("/api/users/me") } ?: return@inSession LinkResult.Unavailable
        if (me.status.value == 401) return@inSession LinkResult.Unauthenticated
        val response = client.sendOrNull {
            post("/api/users/me/social-accounts/${callback.provider.pathName}") {
                contentType(ContentType.Application.Json)
                setBody(OAuthComplete(callback.authorizationCode, callback.redirectUri, callback.state))
            }
        } ?: return@inSession LinkResult.Unavailable
        val code = if (response.status.value == 201) null else response.apiResponse<Error>()?.result?.errorCode
        val result = when {
            response.status.value == 201 || code == "PROVIDER_ALREADY_LINKED" -> LinkResult.Linked
            code == "SOCIAL_ACCOUNT_IN_USE" -> LinkResult.InUse
            response.status.value == 401 -> if (code == "UNAUTHENTICATED") LinkResult.Unauthenticated else LinkResult.Restart
            response.status.value == 400 || response.status.value == 410 -> LinkResult.Restart
            else -> LinkResult.Unavailable
        }
        if (result == LinkResult.Linked && sessionGeneration.value == generation) changeLinked(callback.provider, linked = true)
        return@inSession result
    }

    override suspend fun unlink(provider: SocialProvider): MemberResult = member.inSession {
        val generation = sessionGeneration.value
        val response = client.sendOrNull { delete("/api/users/me/social-accounts/${provider.pathName}") } ?: return@inSession MemberResult.Unavailable
        if (response.status.value != 204 && response.status.value != 404) {
            return@inSession memberFailure(response.status.value, response.apiResponse<Error>()?.result?.errorCode)
        }
        if (sessionGeneration.value == generation) changeLinked(provider, linked = false)
        return@inSession MemberResult.Done
    }

    private fun changeLinked(provider: SocialProvider, linked: Boolean) {
        profileRevision.updateAndGet { it + 1 }
        state.value = state.value.copy(linkedProviders = if (linked) state.value.linkedProviders + provider else state.value.linkedProviders - provider)
    }

    override fun logOut() {
        if (member?.id != null) member.set(null) else clearProfile()
    }

    override fun clearProfile() {
        sessionGeneration.updateAndGet { it + 1 }
        state.value = UserProfile("", "", emptySet())
        instruments?.clear()
    }

    /** [hasPassword]는 서버에 아직 없는 요청 중인 필드다. 오지 않으면 null로 둔다. */
    @Serializable private class MyInfo(
        val nickname: String,
        val instrumentId: String,
        val notationMode: String,
        val linkedProviders: List<Provider> = emptyList(),
        val email: String? = null,
        val hasPassword: Boolean? = null,
    )
    @Serializable private class WithdrawalTicket(val withdrawalTicket: String)
    @Serializable private class WithdrawalTicketBody(val withdrawalTicket: String)
    @Serializable private class OAuthComplete(val authorizationCode: String, val redirectUri: String, val state: String)
    @Serializable private class InstrumentPreference(val instrumentId: String)
    @Serializable private class Preferences(val instrumentId: String, val notationMode: String)
    @Serializable private class Provider(val provider: String)
    @Serializable private class Nickname(val nickname: String)
    @Serializable private class PasswordChange(val currentPassword: String, val newPassword: String)
    @Serializable private class CurrentPassword(val currentPassword: String)
    @Serializable private class PasswordChanged(val changed: Boolean)
    @Serializable private class Withdrawn(val withdrawn: Boolean)
    @Serializable private class Error(val errorCode: String)
}

private fun passwordFailure(status: Int, code: String?): PasswordCheckResult = when {
    code == "CURRENT_PASSWORD_MISMATCH" -> PasswordCheckResult.WrongPassword
    status == 401 -> PasswordCheckResult.Unauthenticated
    status == 429 -> PasswordCheckResult.RateLimited
    status == 400 -> PasswordCheckResult.InvalidInput
    else -> PasswordCheckResult.Unavailable
}

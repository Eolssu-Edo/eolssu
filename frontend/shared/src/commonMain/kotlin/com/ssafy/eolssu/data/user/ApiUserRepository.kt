package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable

/** /me의 사용자 식별자는 서버 쿠키에서 결정한다. 이메일은 로그인한 세션의 메모리에만 둔다. */
class ApiUserRepository(private val client: HttpClient, private val cookies: SecureCookiesStorage?, private val instruments: InstrumentRepository? = null) : UserRepository {
    private val state = MutableStateFlow(UserProfile("", "", emptySet()))
    override val profile = state.asStateFlow()
    private val sessionGeneration = MutableStateFlow(0L)
    private val profileRevision = MutableStateFlow(0L)

    override suspend fun refresh(email: String?): MemberResult {
        val generation = sessionGeneration.value
        val revision = profileRevision.value
        val response = client.sendOrNull { get("/api/users/me") } ?: return MemberResult.Unavailable
        if (response.status.value != 200) return memberFailure(response.status.value)
        val result = response.apiResponse<MyInfo>()?.takeIf { it.isSuccess }?.result ?: return MemberResult.Unavailable
        if (sessionGeneration.value != generation) return MemberResult.Unauthenticated
        if (profileRevision.value != revision) return MemberResult.Unavailable
        state.value = UserProfile(result.nickname, email ?: state.value.email, result.linkedProviders.mapNotNull { provider ->
            SocialProvider.entries.firstOrNull { it.name.equals(provider.provider, ignoreCase = true) }
        }.toSet(), result.instrumentId, result.notationMode)
        if (instruments != null && !instruments.applyServerId(result.instrumentId)) return MemberResult.Unavailable
        return MemberResult.Done
    }

    override suspend fun changeInstrument(instrumentId: String): MemberResult {
        val generation = sessionGeneration.value
        if (!instrumentId.matches(Regex("[1-9][0-9]{0,17}"))) return MemberResult.InvalidInput
        val response = client.sendOrNull {
            patch("/api/users/me/preferences") { contentType(ContentType.Application.Json); setBody(InstrumentPreference(instrumentId)) }
        } ?: return MemberResult.Unavailable
        if (response.status.value != 200) return memberFailure(response.status.value)
        val saved = response.apiResponse<Preferences>()?.takeIf { it.isSuccess }?.result ?: return MemberResult.Unavailable
        if (sessionGeneration.value != generation) return MemberResult.Unauthenticated
        if (saved.instrumentId != instrumentId) return MemberResult.Unavailable
        profileRevision.updateAndGet { it + 1 }
        state.value = state.value.copy(instrumentId = saved.instrumentId, notationMode = saved.notationMode)
        return MemberResult.Done
    }

    override suspend fun changeNickname(nickname: String): MemberResult {
        val response = client.sendOrNull {
            patch("/api/users/me") { contentType(ContentType.Application.Json); setBody(Nickname(nickname)) }
        } ?: return MemberResult.Unavailable
        if (response.status.value != 200) return memberFailure(response.status.value)
        val result = response.apiResponse<Nickname>()?.takeIf { it.isSuccess }?.result ?: return MemberResult.Unavailable
        profileRevision.updateAndGet { it + 1 }
        state.value = state.value.copy(nickname = result.nickname)
        return MemberResult.Done
    }

    override suspend fun changePassword(current: String, new: String): PasswordCheckResult {
        val response = client.sendOrNull {
            patch("/api/users/me/password") { contentType(ContentType.Application.Json); setBody(PasswordChange(current, new)) }
        } ?: return PasswordCheckResult.Unavailable
        if (response.status.value == 200 && response.apiResponse<PasswordChanged>()?.takeIf { it.isSuccess }?.result?.changed == true) return PasswordCheckResult.Done
        return passwordFailure(response.status.value, response.apiResponse<Error>()?.result?.errorCode)
    }

    override suspend fun withdraw(password: String): PasswordCheckResult {
        val response = client.sendOrNull {
            delete("/api/users/me") { contentType(ContentType.Application.Json); setBody(CurrentPassword(password)) }
        } ?: return PasswordCheckResult.Unavailable
        if (response.status.value == 200 && response.apiResponse<Withdrawn>()?.takeIf { it.isSuccess }?.result?.withdrawn == true) {
            cookies?.clear()
            logOut()
            return PasswordCheckResult.Done
        }
        return passwordFailure(response.status.value, response.apiResponse<Error>()?.result?.errorCode)
    }

    // 제공자 OAuth 계약·토큰이 없으므로 연결/해제를 성공으로 만들지 않는다.
    override fun link(provider: SocialProvider): Boolean = false
    override fun unlink(provider: SocialProvider): Boolean = false
    override fun logOut() { sessionGeneration.updateAndGet { it + 1 }; state.value = UserProfile("", "", emptySet()); instruments?.clear() }

    @Serializable private class MyInfo(val nickname: String, val instrumentId: String, val notationMode: String, val linkedProviders: List<Provider> = emptyList())
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

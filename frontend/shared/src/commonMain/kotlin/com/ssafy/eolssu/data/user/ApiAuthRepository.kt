package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 이메일 로그인(AUTH-04, `POST /api/auth/sign-in`)과 로그아웃(AUTH-03, `POST /api/auth/sign-out`)을 서버로 한다.
 * 로그인 쿠키는 서버가 내려 주고 HTTP 클라이언트가 보관한다(웹은 브라우저, 앱은 [cookies]).
 * 인증번호(`sendCode`·`confirmCode`)는 가입 API를 붙일 때 옮기고, 그 전에는 [fallback]이 맡는다.
 *
 * 로그아웃은 화면이 바로 넘어가도 끝나도록 앱 전체의 [scope]에서 한다. 서버 요청이 실패해도 기기의 쿠키는 지운다.
 */
class ApiAuthRepository(
    private val client: HttpClient,
    private val cookies: SecureCookiesStorage?,
    private val scope: CoroutineScope,
    private val fallback: AuthRepository,
) : AuthRepository by fallback {
    private var signingOut: Job? = null

    override suspend fun logIn(email: String, password: String): LoginResult {
        // 로그아웃이 끝나기 전에 로그인하면 새 쿠키가 지워질 수 있다.
        signingOut?.join()
        val response = client.sendOrNull {
            post("/api/auth/sign-in") {
                contentType(ContentType.Application.Json)
                setBody(SignInRequest(email.trim(), password))
            }
        } ?: return LoginResult.Unavailable
        return when (response.status.value) {
            200 -> LoginResult.Success(withdrawalCancelled = response.apiResponse<SignedIn>()?.result?.withdrawalCancelled == true)
            400, 401 -> LoginResult.WrongCredentials
            429 -> LoginResult.Locked
            else -> LoginResult.Unavailable
        }
    }

    override fun logOut() {
        signingOut = scope.launch {
            client.sendOrNull { post("/api/auth/sign-out") }
            cookies?.clear()
        }
    }

    @Serializable
    private class SignInRequest(val email: String, val password: String)

    @Serializable
    private class SignedIn(val withdrawalCancelled: Boolean = false)
}

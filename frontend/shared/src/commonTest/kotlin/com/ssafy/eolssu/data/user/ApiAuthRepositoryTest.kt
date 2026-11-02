package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 이메일 로그인·로그아웃 API 연결(AUTH-03·04). 서버는 MockEngine으로 대신한다. */
class ApiAuthRepositoryTest {

    private val requests = mutableListOf<String>()
    private val bodies = mutableListOf<String>()
    private val member = SignedInMember(FakeKeyValueStore())

    private fun TestScope.repository(
        cookies: SecureCookiesStorage? = null,
        respond: (path: String) -> Pair<HttpStatusCode, String>?,
    ): ApiAuthRepository {
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            (request.body as? TextContent)?.let { bodies += it.text }
            val (status, body) = respond(request.url.encodedPath) ?: error("연결 실패")
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return ApiAuthRepository(createHttpClient(engine, ServerAddress.Production, cookies), cookies, member, this)
    }

    @Test
    fun signInSendsCredentialsAndReadsWithdrawalCancelled() = runTest {
        val repository = repository {
            HttpStatusCode.OK to """{"httpStatus":"OK","isSuccess":true,"message":"로그인 성공","code":200,
                "result":{"userId":"1","nickname":"얼쑤","instrumentId":"1","notationMode":"HANGUL","withdrawalCancelled":true}}"""
        }

        assertEquals(LoginResult.Success(withdrawalCancelled = true), repository.logIn(" user@example.com ", "eolssu1!"))
        assertEquals("1", member.id)
        assertEquals(listOf("/api/auth/sign-in"), requests)
        assertEquals("""{"email":"user@example.com","password":"eolssu1!"}""", bodies.single())
    }

    @Test
    fun errorResponsesMapToLoginResults() = runTest {
        member.set("1") // 실패하면 쿠키가 바뀌지 않으므로 이전 회원이 남는다
        val cases = mapOf(
            HttpStatusCode.Unauthorized to LoginResult.WrongCredentials,
            HttpStatusCode.BadRequest to LoginResult.WrongCredentials,
            HttpStatusCode.TooManyRequests to LoginResult.Locked(null),
            HttpStatusCode.ServiceUnavailable to LoginResult.Unavailable,
        )
        for ((status, expected) in cases) {
            val repository = repository { status to """{"isSuccess":false,"message":"","code":${status.value},"result":{"errorCode":"X"}}""" }
            assertEquals(expected, repository.logIn("user@example.com", "eolssu1!"), "$status")
        }
        assertEquals(LoginResult.Unavailable, repository { null }.logIn("user@example.com", "eolssu1!"))
        assertEquals("1", member.id)
    }

    @Test
    fun lockedLoginKeepsRetryTimeFromServer() = runTest {
        val repository = repository {
            HttpStatusCode.TooManyRequests to
                """{"isSuccess":false,"message":"","code":429,"result":{"errorCode":"TOO_MANY_REQUESTS","details":{"retryAvailableAt":"2026-11-02T05:30:00Z"}}}"""
        }
        assertEquals(LoginResult.Locked(1_793_597_400_000L), repository.logIn("user@example.com", "eolssu1!"))
    }

    @Test
    fun logOutTellsServerAndClearsAppCookiesBeforeNextLogIn() = runTest {
        member.set("1")
        val store = FakeSecureStore().apply { putString("auth.cookies", "[]") }
        val cookies = SecureCookiesStorage(store) { 0L }
        val repository = repository(cookies) { path ->
            if (path == "/api/auth/sign-out") HttpStatusCode.OK to """{"isSuccess":true,"result":{"signedOut":true}}""" else null
        }

        repository.logOut()
        repository.logIn("user@example.com", "eolssu1!") // 로그아웃이 끝난 뒤에 보낸다

        assertEquals(listOf("/api/auth/sign-out", "/api/auth/sign-in"), requests)
        assertTrue(store.values.isEmpty())
        assertNull(member.id)
    }

    @Test
    fun logOutClearsCookiesEvenWhenServerIsUnreachable() = runTest {
        val store = FakeSecureStore().apply { putString("auth.cookies", "[]") }
        val repository = repository(SecureCookiesStorage(store) { 0L }) { null }

        repository.logOut()
        repository.logIn("user@example.com", "eolssu1!")

        assertTrue(store.values.isEmpty())
    }
}

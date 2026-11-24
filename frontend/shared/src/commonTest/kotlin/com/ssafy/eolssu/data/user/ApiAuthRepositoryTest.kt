package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.storage.FakeSecureStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
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
    private val device = FakeKeyValueStore()
    private val member = SignedInMember(device)

    private fun TestScope.repository(
        cookies: SecureCookiesStorage? = null,
        store: KeyValueStore? = null,
        respond: (path: String) -> Pair<HttpStatusCode, String>?,
    ): ApiAuthRepository {
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            (request.body as? TextContent)?.let { bodies += it.text }
            val (status, body) = respond(request.url.encodedPath) ?: error("연결 실패")
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return ApiAuthRepository(createHttpClient(engine, ServerAddress.Production, cookies), cookies, member, this, store = store)
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

    /**
     * 기다리지 않는 로그아웃·재전송이 끝날 때까지 기다린다. HTTP 엔진은 테스트의 가상 시간 밖(웹은 브라우저)에서 돌아
     * `advanceUntilIdle`로는 끝을 기다리지 못한다.
     */
    private suspend fun TestScope.settle() = this.coroutineContext.job.children.toList().joinAll()

    private val signedOut = HttpStatusCode.OK to """{"isSuccess":true,"result":{"signedOut":true}}"""
    private val signedIn = HttpStatusCode.OK to """{"isSuccess":true,"result":{"userId":"2","withdrawalCancelled":false}}"""

    @Test
    fun webLogOutThatMissesTheServerIsSentAgainOnTheNextStart() = runTest {
        member.set("1")
        // 웹: 쿠키는 브라우저가 보관해 앱이 지울 수 없다(cookies 없음).
        repository(store = device) { null }.logOut()
        settle()
        assertNull(member.id, "화면은 바로 로그아웃 상태가 된다")
        assertEquals("true", device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY], "쿠키가 남았으므로 다시 보낼 표시를 남긴다")

        // 다음 시작: 아직 오프라인이면 표시를 그대로 둔다.
        repository(store = device) { null }.retrySignOut()
        settle()
        assertEquals("true", device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY])

        // 그다음 시작: 서버에 닿으면 표시를 지운다.
        repository(store = device) { signedOut }.retrySignOut()
        settle()
        assertEquals("", device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY])
        assertEquals(List(3) { "/api/auth/sign-out" }, requests)

        repository(store = device) { signedOut }.retrySignOut()
        settle()
        assertEquals(3, requests.size, "표시가 없으면 보내지 않는다")
    }

    @Test
    fun webLogOutMarksThePendingSignOutBeforeSending() = runTest {
        member.set("1")
        var pendingWhenSent: String? = null
        // 응답을 기다리는 동안 탭을 닫아도 표시가 남아 있어야 한다.
        repository(store = device) { pendingWhenSent = device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY]; signedOut }.logOut()
        settle()

        assertEquals("true", pendingWhenSent)
        assertEquals("", device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY], "서버에 닿으면 지운다")
    }

    @Test
    fun serverErrorsKeepThePendingSignOutButOtherAnswersClearIt() = runTest {
        val cases = mapOf(
            HttpStatusCode.ServiceUnavailable to "true",
            HttpStatusCode.InternalServerError to "true",
            // 액세스 토큰이 만료돼 갱신도 실패했다. 갱신 실패 응답이 쿠키를 지운다.
            HttpStatusCode.Unauthorized to "",
            HttpStatusCode.OK to "",
        )
        for ((status, pending) in cases) {
            member.set("1")
            repository(store = device) { status to """{"isSuccess":false,"result":{"errorCode":"X"}}""" }.logOut()
            settle()
            assertEquals(pending, device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY], "$status")
        }
    }

    @Test
    fun pendingSignOutIsDroppedWithoutSendingWhenSignedInAgain() = runTest {
        device.putString(ApiAuthRepository.PENDING_SIGN_OUT_KEY, "true")
        member.set("2") // 오프라인 로그아웃 뒤 다시 로그인했다. 남은 쿠키는 새 로그인의 것이다.

        repository(store = device) { signedOut }.retrySignOut()
        settle()

        assertTrue(requests.isEmpty())
        assertEquals("", device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY])
        assertEquals("2", member.id)
    }

    @Test
    fun logInWaitsForTheRetriedSignOut() = runTest {
        device.putString(ApiAuthRepository.PENDING_SIGN_OUT_KEY, "true")
        val repository = repository(store = device) { path -> if (path == "/api/auth/sign-out") signedOut else signedIn }

        repository.retrySignOut()
        repository.logIn("user@example.com", "eolssu1!")

        assertEquals(listOf("/api/auth/sign-out", "/api/auth/sign-in"), requests, "새 로그인 쿠키를 지우지 않도록 재전송이 끝난 뒤에 로그인한다")
        assertEquals("2", member.id)
    }

    @Test
    fun appClearsItsOwnCookiesAndNeverLeavesAPendingSignOut() = runTest {
        member.set("1")
        val cookies = SecureCookiesStorage(FakeSecureStore().apply { putString("auth.cookies", "[]") }) { 0L }
        val repository = repository(cookies, store = device) { null }

        repository.logOut()
        settle()
        assertNull(device.values[ApiAuthRepository.PENDING_SIGN_OUT_KEY], "앱은 서버에 닿지 못해도 표시를 남기지 않는다")

        device.putString(ApiAuthRepository.PENDING_SIGN_OUT_KEY, "true") // 웹에서 쓰던 표시가 있어도
        repository.retrySignOut()
        settle()
        assertEquals(listOf("/api/auth/sign-out"), requests, "앱은 다시 보내지 않는다")
    }
}

package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.storage.FakeSecureStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 공통 HTTP 클라이언트의 토큰 갱신과 앱 로그인 쿠키 보관. 서버는 MockEngine으로 대신한다. */
class HttpClientsTest {

    private val requests = mutableListOf<String>()

    private fun client(cookies: SecureCookiesStorage? = null, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        createHttpClient(
            MockEngine { request ->
                requests += "${request.method.value} ${request.url.encodedPath}"
                handler(request)
            },
            ServerAddress.Production,
            cookies,
        )

    @Test
    fun unauthorizedRefreshesOnceAndResends() = runTest {
        var refreshed = false
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond("", HttpStatusCode.OK).also { refreshed = true }
                refreshed -> respond("", HttpStatusCode.Created)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        assertEquals(HttpStatusCode.Created, client.post("/api/users/me/score-views").status)
        assertEquals(listOf("POST /api/users/me/score-views", "POST /api/auth/refresh", "POST /api/users/me/score-views"), requests)
    }

    @Test
    fun failedRefreshReturnsOriginalUnauthorized() = runTest {
        val client = client { respond("", HttpStatusCode.Unauthorized) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/users/me").status)
        assertEquals(listOf("GET /api/users/me", "POST /api/auth/refresh"), requests)
    }

    @Test
    fun wrongPasswordIsNotRefreshed() = runTest {
        val client = client { respond("", HttpStatusCode.Unauthorized) }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/auth/sign-in").status)
        assertEquals(listOf("POST /api/auth/sign-in"), requests)
    }

    @Test
    fun oneTimeOAuthRequestsAreNotResent() = runTest {
        val client = client { request ->
            if (request.url.encodedPath == "/api/auth/refresh") respond("", HttpStatusCode.OK) else respond("", HttpStatusCode.Unauthorized)
        }
        val oneTime = listOf(
            "/api/auth/oauth/kakao/start",
            "/api/auth/oauth/kakao",
            "/api/auth/oauth/merge",
            "/api/users/me/social-accounts/naver",
        )

        oneTime.forEach { assertEquals(HttpStatusCode.Unauthorized, client.post(it).status) }
        assertEquals(oneTime.map { "POST $it" }, requests)

        // 마이페이지 연결 start는 1회용 값을 쓰지 않아 평소처럼 갱신 뒤 다시 보낸다.
        requests.clear()
        client.post("/api/users/me/social-accounts/naver/start")
        assertEquals(
            listOf("POST /api/users/me/social-accounts/naver/start", "POST /api/auth/refresh", "POST /api/users/me/social-accounts/naver/start"),
            requests,
        )
    }

    @Test
    fun concurrentUnauthorizedRequestsShareOneRefresh() = runTest {
        var refreshed = false
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond("", HttpStatusCode.OK).also { refreshed = true }
                refreshed -> respond("", HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        val statuses = listOf("/api/a", "/api/b").map { path -> async { client.get(path).status } }.awaitAll()
        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), statuses)
        assertEquals(1, requests.count { it == "POST /api/auth/refresh" })
    }

    @Test
    fun appCookiesAreSentByPathAndSurviveRelaunch() = runTest {
        val store = FakeSecureStore()
        var now = 0L
        val signIn = client(SecureCookiesStorage(store) { now }) { request ->
            if (request.url.encodedPath == "/api/auth/sign-in") {
                respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, listOf(ACCESS, REFRESH)))
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
        signIn.post("/api/auth/sign-in")

        // 앱을 다시 켬: 같은 저장소로 새 클라이언트
        val sentCookies = mutableMapOf<String, String?>()
        val relaunched = client(SecureCookiesStorage(store) { now }) { request ->
            sentCookies[request.url.encodedPath] = request.headers[HttpHeaders.Cookie]
            respond("", HttpStatusCode.OK)
        }
        relaunched.get("/api/users/me")
        relaunched.post("/api/auth/refresh")
        assertEquals("access_token=a1", sentCookies["/api/users/me"])
        assertEquals("access_token=a1; refresh_token=r1", sentCookies["/api/auth/refresh"])

        now = 31 * 60 * 1000L // 액세스 토큰(30분) 만료
        relaunched.get("/api/users/me")
        assertNull(sentCookies["/api/users/me"])
    }

    @Test
    fun clearedCookiesAreRemovedFromStore() = runTest {
        val store = FakeSecureStore()
        val cookies = SecureCookiesStorage(store) { 0L }
        val client = client(cookies) { request ->
            val setCookie = if (request.url.encodedPath == "/api/auth/sign-in") listOf(ACCESS, REFRESH) else listOf(CLEAR_ACCESS)
            respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, setCookie))
        }
        client.post("/api/auth/sign-in")
        client.post("/api/auth/sign-out") // 서버가 access_token을 Max-Age=0으로 지움

        val remaining = SecureCookiesStorage(store) { 0L }
        assertEquals(listOf("refresh_token"), remaining.get(io.ktor.http.Url("https://k15e102.p.ssafy.io/api/auth/refresh")).map { it.name })

        cookies.clear()
        assertEquals(emptyMap(), store.values)
    }

    @Test
    fun errorResponseKeepsStatusNameCodeAndErrorCode() = runTest {
        val client = client {
            respond(
                """{"httpStatus":"SERVICE_UNAVAILABLE","isSuccess":false,"message":"잠시 후 다시","code":503,"result":{"errorCode":"SERVICE_UNAVAILABLE"}}""",
                HttpStatusCode.ServiceUnavailable,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = client.sendCatching { post("/api/practice-sessions/1/recording") }
        val response = assertIs<NetworkResult.Received>(result).response
        assertEquals(503, response.status.value)
        val body = assertNotNull(response.apiResponse<ApiError>())
        assertEquals("SERVICE_UNAVAILABLE", body.httpStatus)
        assertEquals(503, body.code)
        assertEquals("SERVICE_UNAVAILABLE", body.result?.errorCode)
    }

    @Test
    fun connectionFailureAndTimeoutAreDifferent() = runTest {
        assertEquals(NetworkResult.ConnectionFailed, client { error("연결 거부") }.sendCatching { get("/api/users/me") })
        assertEquals(NetworkResult.TimedOut, client { throw ConnectTimeoutException("연결 시간 초과") }.sendCatching { get("/api/users/me") })
    }

    @Test
    fun requestTimeoutCanBeSetPerRequest() = runTest {
        val slow = client {
            delay(300)
            respond("", HttpStatusCode.Created)
        }

        val short = slow.sendCatching { post("/api/a") { timeout { requestTimeoutMillis = 50 } } }
        assertEquals(NetworkResult.TimedOut, short)
        val long = slow.sendCatching { post("/api/a") { timeout { requestTimeoutMillis = 5_000 } } }
        assertEquals(HttpStatusCode.Created, assertIs<NetworkResult.Received>(long).response.status)
    }

    @Test
    fun multipartBodyIsSentAgainAfterRefresh() = runTest {
        val audio = ByteArray(4_096) { it.toByte() }
        val bodies = mutableListOf<ByteArray>()
        var refreshed = false
        val client = client { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond("", HttpStatusCode.OK).also { refreshed = true }
                else -> {
                    bodies += request.body.toByteArray()
                    respond("", if (refreshed) HttpStatusCode.Created else HttpStatusCode.Unauthorized)
                }
            }
        }
        val fileHeaders = Headers.build {
            append(HttpHeaders.ContentType, "audio/mp4")
            append(HttpHeaders.ContentDisposition, "filename=\"take.m4a\"")
        }

        val response = client.post("/api/practice-sessions/1/recording") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("bytes", audio, fileHeaders)
                        append("stream", ChannelProvider(audio.size.toLong()) { ByteReadChannel(audio) }, fileHeaders)
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(2, bodies.size)
        assertContentEquals(bodies[0], bodies[1])
        assertTrue(bodies[1].size > audio.size * 2)
    }

    private companion object {
        const val ACCESS = "access_token=a1; Path=/api; Max-Age=1800; Secure; HttpOnly; SameSite=Lax"
        const val REFRESH = "refresh_token=r1; Path=/api/auth; Max-Age=1209600; Secure; HttpOnly; SameSite=Lax"
        const val CLEAR_ACCESS = "access_token=; Path=/api; Max-Age=0; Secure; HttpOnly; SameSite=Lax"
    }
}

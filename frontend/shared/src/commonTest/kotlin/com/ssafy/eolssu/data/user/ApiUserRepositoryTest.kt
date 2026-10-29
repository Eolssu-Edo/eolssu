package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeSecureStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiUserRepositoryTest {
    @Test fun serverEmailWinsAndWithdrawalClearsMemberOwnership() = runTest {
        val member = com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore())
        member.changing { member.set("42") }
        val client = createHttpClient(MockEngine { request ->
            val payload = if (request.method == HttpMethod.Get)
                """{"nickname":"회원","email":"actual@example.test","instrumentId":"1","notationMode":"HANJA"}"""
            else """{"withdrawn":true}"""
            respond("""{"isSuccess":true,"result":$payload}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val repo = ApiUserRepository(client, null, member = member)
        assertEquals(MemberResult.Done, repo.refresh("old@example.test"))
        assertEquals("actual@example.test", repo.profile.value.email)
        assertEquals(PasswordCheckResult.Done, repo.withdraw("test-password"))
        assertEquals(null, member.id)
        client.close()
    }
    @Test
    fun profileAndPasswordChangesUseAcceptedServerValues() = runTest {
        var passwordAccepted = false
        val client = createHttpClient(MockEngine { request ->
            when {
                request.method == HttpMethod.Get -> respond("""{"isSuccess":true,"result":{"nickname":"Before","instrumentId":"42","notationMode":"HANJA","linkedProviders":[{"provider":"KAKAO"}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("password") -> {
                    assertEquals("""{"currentPassword":"current1!","newPassword":"changed1!"}""", (request.body as TextContent).text)
                    respond("""{"isSuccess":true,"result":{"changed":$passwordAccepted}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> {
                    assertEquals(HttpMethod.Patch, request.method)
                    assertEquals("""{"nickname":"Requested"}""", (request.body as TextContent).text)
                    respond("""{"isSuccess":true,"result":{"nickname":"Accepted"}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }, ServerAddress.Production)
        val repo = ApiUserRepository(client, null)
        assertEquals(MemberResult.Done, repo.refresh())
        assertEquals("", repo.profile.value.email)
        assertEquals(MemberResult.Done, repo.changeNickname("Requested"))
        assertEquals("Accepted", repo.profile.value.nickname)
        assertEquals(PasswordCheckResult.Unavailable, repo.changePassword("current1!", "changed1!"))
        passwordAccepted = true
        assertEquals(PasswordCheckResult.Done, repo.changePassword("current1!", "changed1!"))
        client.close()
    }

    @Test
    fun withdrawalRequiresAcceptedRemoteDeletionBeforeClearingProfileAndCookies() = runTest {
        for (accepted in listOf(false, true)) {
            val store = FakeSecureStore().apply { putString("auth.cookies", "[]") }
            val cookies = SecureCookiesStorage(store) { 0L }
            val client = createHttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Get) {
                    respond("""{"isSuccess":true,"result":{"nickname":"테스트회원","instrumentId":"42","notationMode":"HANJA","linkedProviders":[]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("/api/users/me", request.url.encodedPath)
                    assertEquals("{\"currentPassword\":\"test-pass1!\"}", (request.body as TextContent).text)
                    if (accepted) respond("""{"isSuccess":true,"result":{"withdrawn":true}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    else respond("""{"isSuccess":false,"result":{"errorCode":"CURRENT_PASSWORD_MISMATCH"}}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }, ServerAddress.Production)
            val repo = ApiUserRepository(client, cookies)
            assertEquals(MemberResult.Done, repo.refresh("member@example.test"))
            assertEquals(if (accepted) PasswordCheckResult.Done else PasswordCheckResult.WrongPassword, repo.withdraw("test-pass1!"))
            assertEquals(if (accepted) "" else "테스트회원", repo.profile.value.nickname)
            assertEquals(accepted, store.values.isEmpty())
            client.close()
        }
    }

    @Test
    fun unreachableAndMalformedSuccessDoNotReportDeletion() = runTest {
        for (malformed in listOf(false, true)) {
            val client = createHttpClient(MockEngine {
                if (!malformed) error("offline test")
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            assertEquals(PasswordCheckResult.Unavailable, ApiUserRepository(client, null).withdraw("test-pass1!"))
            client.close()
        }
    }
}

package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class MemberApiTest {
    @Test
    fun signupSendsSelectedAgreementVersionsAndUsesServerInstrumentDefault() = runTest {
        val client = createHttpClient(MockEngine { request ->
            assertEquals("/api/auth/sign-up", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("verified-test", body.getValue("verificationToken").jsonPrimitive.content)
            assertFalse("instrumentId" in body)
            assertEquals("v3", body.getValue("agreements").jsonArray[0].jsonObject.getValue("version").jsonPrimitive.content)
            assertEquals("false", body.getValue("agreements").jsonArray[1].jsonObject.getValue("agreed").jsonPrimitive.content)
            respond("""{"isSuccess":true,"result":{"userId":"42"}}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val request = Registration("member@example.test", "test-pass1!", "Member", "verified-test", listOf(TermAgreement("17", "v3", true), TermAgreement("19", "v2", false)))
        assertEquals(MemberResult.Done, ApiAuthRepository(client, null, com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()), this).signUp(request))
        client.close()
    }

    @Test
    fun expiredVerificationAndTermsChangesNeverCompleteSignup() = runTest {
        for ((code, expected) in listOf("EMAIL_NOT_VERIFIED" to MemberResult.VerificationRequired, "TERMS_AGREEMENT_REQUIRED" to MemberResult.TermsRequired, "EMAIL_ALREADY_EXISTS" to MemberResult.EmailExists)) {
            val client = createHttpClient(MockEngine {
                respond("""{"isSuccess":false,"result":{"errorCode":"$code"}}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            assertEquals(expected, ApiAuthRepository(client, null, com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()), this).signUp(Registration("member@example.test", "test-pass1!", "Member", "old-token", emptyList())))
            client.close()
        }
    }

    @Test
    fun resetUsesVerificationTokenAndOnlyNoContentCompletes() = runTest {
        for (accepted in listOf(false, true)) {
            val client = createHttpClient(MockEngine { request ->
                assertEquals("/api/auth/password-reset", request.url.encodedPath)
                assertEquals("""{"verificationToken":"verified-reset","newPassword":"new-pass1!"}""", (request.body as TextContent).text)
                if (accepted) respond("", HttpStatusCode.NoContent)
                else respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            assertEquals(if (accepted) MemberResult.Done else MemberResult.Unavailable, ApiAuthRepository(client, null, com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()), this).resetPassword("verified-reset", "new-pass1!"))
            client.close()
        }
    }

    @Test
    fun invalidSuccessAndNetworkFailureNeverBecomeAuthentication() = runTest {
        for (body in listOf(null, "{}", """{"isSuccess":false,"result":{"userId":"1","withdrawalCancelled":false}}""")) {
            val client = createHttpClient(MockEngine {
                if (body == null) error("offline test")
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }, ServerAddress.Production)
            assertEquals(LoginResult.Unavailable, ApiAuthRepository(client, null, com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()), this).logIn("member@example.test", "test-pass1!"))
            client.close()
        }
    }

    @Test
    fun verificationUsesServerIdAndKeepsLeadingZeroCode() = runTest {
        val calls = mutableListOf<String>()
        val client = createHttpClient(MockEngine { request ->
            calls += request.url.encodedPath
            if (request.url.encodedPath.endsWith("confirm")) {
                assertEquals("{\"code\":\"012345\"}", (request.body as TextContent).text)
                respond("""{"isSuccess":true,"result":{"verificationToken":"test-token","email":"member@example.test","expiresAt":"2026-10-29T02:10:00Z"}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                assertEquals("{\"email\":\"member@example.test\",\"purpose\":\"SIGN_UP\"}", (request.body as TextContent).text)
                respond("""{"isSuccess":true,"result":{"verificationId":"42","expiresAt":"2026-10-29T02:03:00Z","resendAvailableAt":"2026-10-29T02:01:00Z"}}""", HttpStatusCode.Accepted, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }, ServerAddress.Production)
        val repository = ApiAuthRepository(client, null, com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()), this)
        val sent = assertIs<SendCodeResult.Sent>(repository.sendCode("member@example.test", VerificationPurpose.SignUp))
        assertEquals("42", sent.verificationId)
        assertEquals("test-token", assertIs<ConfirmCodeResult.Confirmed>(repository.confirmCode(sent.verificationId, "012345")).verificationToken)
        assertEquals(listOf("/api/auth/email-verifications", "/api/auth/email-verifications/42/confirm"), calls)
        client.close()
    }
}

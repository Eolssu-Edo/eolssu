package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider

import kotlin.test.*
import org.junit.jupiter.api.Test

class OAuthIdentityParserTests {
    @Test fun `kakao requires both valid and verified email flags`() {
        val identity = OAuthIdentityParser.parse(SocialProvider.KAKAO,
            """{"id":1234567890123456,"kakao_account":{"email":"member@example.test","is_email_valid":true,"is_email_verified":true}}""")
        assertEquals("1234567890123456", identity.subject)
        assertEquals("member@example.test", identity.email)
        assertTrue(identity.emailVerified)
        for (flags in listOf("\"is_email_valid\":false,\"is_email_verified\":true",
            "\"is_email_valid\":true,\"is_email_verified\":\"true\"", "\"is_email_verified\":true")) {
            assertFalse(OAuthIdentityParser.parse(SocialProvider.KAKAO,
                """{"id":42,"kakao_account":{"email":"a@example.test",$flags}}""").emailVerified)
        }
    }

    @Test fun `google keeps case sensitive subject and requires boolean verified flag`() {
        val identity = OAuthIdentityParser.parse(SocialProvider.GOOGLE,
            """{"sub":"AbC-123","email":"a@gmail.com","email_verified":true}""")
        assertEquals("AbC-123", identity.subject)
        assertTrue(identity.emailVerified)
        assertFalse(OAuthIdentityParser.parse(SocialProvider.GOOGLE,
            """{"sub":"AbC-123","email":"a@example.test","email_verified":"true"}""").emailVerified)
        val absent = OAuthIdentityParser.parse(SocialProvider.GOOGLE, """{"sub":"123","email_verified":true}""")
        assertNull(absent.email)
        assertFalse(absent.emailVerified)
    }

    @Test fun `google third party verified email requires fresh identity proof unless workspace is authoritative`() {
        assertFalse(OAuthIdentityParser.parse(SocialProvider.GOOGLE,
            """{"sub":"old-owner","email":"recycled@example.test","email_verified":true}""").emailVerified)
        assertFalse(OAuthIdentityParser.parse(SocialProvider.GOOGLE,
            """{"sub":"old-owner","email":"recycled@example.test","email_verified":true,"hd":""}""").emailVerified)
        assertTrue(OAuthIdentityParser.parse(SocialProvider.GOOGLE,
            """{"sub":"workspace-owner","email":"member@example.test","email_verified":true,"hd":"example.test"}""").emailVerified)
        assertFalse(OAuthIdentityParser.parse(SocialProvider.GOOGLE,
            """{"sub":"workspace-owner","email":"member@example.test","email_verified":false,"hd":"example.test"}""").emailVerified)
    }

    @Test fun `naver email alone is not evidence of verified email`() {
        val identity = OAuthIdentityParser.parse(SocialProvider.NAVER,
            """{"resultcode":"00","response":{"id":"naver-subject","email":"a@example.test"}}""")
        assertEquals("naver-subject", identity.subject)
        assertFalse(identity.emailVerified)
    }

    @Test fun `invalid profiles fail without exposing provider response`() {
        for ((provider, body) in listOf(
            SocialProvider.KAKAO to """{"id":-1}""",
            SocialProvider.KAKAO to """{"id":1.5}""",
            SocialProvider.GOOGLE to """{"email":"private@example.test"}""",
            SocialProvider.GOOGLE to """{"sub":123}""",
            SocialProvider.NAVER to """{"resultcode":"024","response":{"id":"123"}}""",
            SocialProvider.NAVER to "not-json-private-response",
        )) {
            val failure = assertFailsWith<OAuthProfileRejected> { OAuthIdentityParser.parse(provider, body) }
            assertEquals("OAuth profile rejected", failure.message)
            assertNull(failure.cause)
        }
    }
}

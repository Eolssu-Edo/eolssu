package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.ssafy.eolssu.backend.feature.auth.domain.OAuthRemoteUnavailable
import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClient
import java.net.URI
import kotlin.test.*

class RestOAuthGatewayTests {
    private val redirect = "https://example.test/callback"
    private val settings = OAuthClientSettings("test-client", "test-secret", listOf(redirect))
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val gateway = RestOAuthGateway(OAuthProviderProperties(settings,settings,settings),builder.build())

    @Test fun `exchanges codes by form then fetches userinfo with server only token`() {
        for ((provider,tokenUrl,userUrl,profile) in listOf(
            listOf(SocialProvider.KAKAO,"https://kauth.kakao.com/oauth/token","https://kapi.kakao.com/v2/user/me","""{"id":42}"""),
            listOf(SocialProvider.GOOGLE,"https://oauth2.googleapis.com/token","https://openidconnect.googleapis.com/v1/userinfo","""{"sub":"42"}"""),
            listOf(SocialProvider.NAVER,"https://nid.naver.com/oauth2.0/token","https://openapi.naver.com/v1/nid/me","""{"resultcode":"00","response":{"id":"42"}}"""),
        )) {
            server.expect(requestTo(tokenUrl.toString())).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=code%2Bvalue")))
                .andRespond(withSuccess("""{"access_token":"provider-secret-token"}""",MediaType.APPLICATION_JSON))
            server.expect(requestTo(userUrl.toString())).andExpect(header("Authorization","Bearer provider-secret-token"))
                .andRespond(withSuccess(profile.toString(),MediaType.APPLICATION_JSON))
            assertEquals("42",gateway.identify(provider as SocialProvider,redirect,"code+value","state","verifier").subject)
            server.verify()
            server.reset()
        }
    }

    @Test fun `missing settings and unregistered redirects never call a provider`() {
        assertFalse(RestOAuthGateway(OAuthProviderProperties(),builder.build()).ready(SocialProvider.GOOGLE))
        assertFalse(gateway.allows(SocialProvider.GOOGLE,"https://evil.test/callback"))
        assertFailsWith<OAuthProfileRejected> { gateway.identify(SocialProvider.GOOGLE,"https://evil.test/callback","code","state","binding") }
        server.verify()
    }

    @Test fun `authorization url preserves state and uses PKCE for google`() {
        val url = URI(gateway.authorizationUrl(SocialProvider.GOOGLE,redirect,"state+value","verifier"))
        assertEquals("accounts.google.com",url.host)
        assertTrue(url.rawQuery.contains("state=state%2Bvalue"),url.toString())
        assertTrue(url.rawQuery.contains("code_challenge_method=S256"))
        assertFalse(url.toString().contains("test-secret"))
    }

    @Test fun `provider rejection and outage are sanitized and distinguished`() {
        server.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withBadRequest().body("private-provider-detail"))
        assertNull(assertFailsWith<OAuthProfileRejected> { gateway.identify(SocialProvider.GOOGLE,redirect,"code","state","binding") }.cause)
        server.verify(); server.reset()
        server.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withServerError().body("private-provider-detail"))
        assertEquals("OAuth provider unavailable",assertFailsWith<OAuthRemoteUnavailable> { gateway.identify(SocialProvider.GOOGLE,redirect,"code","state","binding") }.message)
        server.verify()
    }
}

package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.auth.domain.OAuthRemoteUnavailable
import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider
import java.net.URI
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClient

class AppleOAuthGatewayTests {
    private val now = Instant.now()
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val redirect = "https://example.test/api/auth/ios/oauth/apple/callback"
    private val ecKeys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\\n" + Base64.getMimeEncoder().encodeToString(ecKeys.private.encoded).replace("\r\n", "\\n") + "\\n-----END PRIVATE KEY-----"
    private val appleKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val settings = AppleSettings("com.ssafy.eolssu", "TEAM123456", "KEY1234567", pem, listOf(redirect))
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val gateway = AppleOAuthGateway(settings, builder.build(), NimbusJwtDecoder.withPublicKey(appleKeys.public as RSAPublicKey).build(), clock)
    private val state = "s".repeat(43)
    private val devRedirect = "https://example.test/api/auth/ios-dev/oauth/apple/callback"
    private val devGateway = AppleOAuthGateway(
        AppleSettings("com.ssafy.eolssu", "TEAM123456", "KEY1234567", pem, listOf(redirect), "com.ssafy.eolssu.dev", listOf(devRedirect)),
        builder.build(), NimbusJwtDecoder.withPublicKey(appleKeys.public as RSAPublicKey).build(), clock)

    private fun formOf(body: String) = body.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }

    private fun nonce(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun idToken(audience: String = "com.ssafy.eolssu", nonce: String? = nonce(state), issuer: String = "https://appleid.apple.com"): String {
        val claims = JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("001234.apple-user")
            .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(600)))
            .claim("email", "relay@privaterelay.appleid.com").claim("email_verified", true)
        if (nonce != null) claims.claim("nonce", nonce)
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID("apple").build(), claims.build()).apply { sign(RSASSASigner(appleKeys.private)) }.serialize()
    }

    private fun expectToken(idToken: String) {
        server.expect(requestTo("https://appleid.apple.com/auth/token")).andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(withSuccess("""{"access_token":"a","refresh_token":"apple-refresh","id_token":"$idToken"}""", MediaType.APPLICATION_JSON))
    }

    @Test fun `authorization url carries state and a nonce derived from it`() {
        val url = URI(gateway.authorizationUrl(SocialProvider.APPLE, redirect, state, "binding"))
        assertEquals("https", url.scheme)
        assertEquals("appleid.apple.com", url.host)
        val query = url.rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        assertEquals(state, query["state"])
        assertEquals(nonce(state), query["nonce"])
        assertEquals("com.ssafy.eolssu", query["client_id"])
        assertFalse(url.toString().contains("PRIVATE"))
    }

    @Test fun `code exchange signs an ES256 client secret and returns the unverified email and refresh token`() {
        var form = ""
        server.expect(requestTo("https://appleid.apple.com/auth/token")).andExpect(method(HttpMethod.POST))
            .andExpect { form = (it as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString }
            .andRespond(withSuccess("""{"access_token":"a","refresh_token":"apple-refresh","id_token":"${idToken()}"}""", MediaType.APPLICATION_JSON))
        val identity = gateway.identify(SocialProvider.APPLE, redirect, "apple-code", state, "binding")
        server.verify()
        assertEquals("001234.apple-user", identity.subject)
        assertEquals("relay@privaterelay.appleid.com", identity.email)
        assertFalse(identity.emailVerified)
        assertEquals("apple-refresh", identity.providerToken)
        val fields = form.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        assertEquals("authorization_code", fields["grant_type"])
        assertEquals("apple-code", fields["code"])
        assertEquals("com.ssafy.eolssu", fields["client_id"])
        assertNull(fields["redirect_uri"])
        val secret = SignedJWT.parse(fields["client_secret"])
        assertEquals(JWSAlgorithm.ES256, secret.header.algorithm)
        assertEquals("KEY1234567", secret.header.keyID)
        assertTrue(secret.verify(ECDSAVerifier(ecKeys.public as ECPublicKey)))
        assertEquals("TEAM123456", secret.jwtClaimsSet.issuer)
        assertEquals("com.ssafy.eolssu", secret.jwtClaimsSet.subject)
        assertEquals(listOf("https://appleid.apple.com"), secret.jwtClaimsSet.audience)
        assertTrue(secret.jwtClaimsSet.expirationTime.toInstant() <= now.plusSeconds(600))
    }

    @Test fun `tokens for another nonce audience or issuer are rejected`() {
        for (token in listOf(idToken(nonce = nonce("other")), idToken(nonce = null), idToken(audience = "com.other.app"), idToken(issuer = "https://evil.test"))) {
            expectToken(token)
            assertFailsWith<OAuthProfileRejected> { gateway.identify(SocialProvider.APPLE, redirect, "apple-code", state, "binding") }
            server.verify(); server.reset()
        }
    }

    @Test fun `apple outage and unknown redirects are distinguished`() {
        server.expect(requestTo("https://appleid.apple.com/auth/token")).andRespond(withServerError())
        assertFailsWith<OAuthRemoteUnavailable> { gateway.identify(SocialProvider.APPLE, redirect, "apple-code", state, "binding") }
        server.verify(); server.reset()
        server.expect(requestTo("https://appleid.apple.com/auth/token")).andRespond(withBadRequest().body("""{"error":"invalid_grant"}"""))
        assertFailsWith<OAuthProfileRejected> { gateway.identify(SocialProvider.APPLE, redirect, "apple-code", state, "binding") }
        server.verify()
        assertFalse(gateway.allows(SocialProvider.APPLE, "https://evil.test/callback"))
        assertFalse(AppleOAuthGateway(AppleSettings(), builder.build(), NimbusJwtDecoder.withPublicKey(appleKeys.public as RSAPublicKey).build(), clock).ready(SocialProvider.APPLE))
    }

    @Test fun `revoke sends the refresh token with a fresh client secret`() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke")).andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token=apple-refresh")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("token_type_hint=refresh_token")))
            .andRespond(withSuccess())
        gateway.revoke(SocialProvider.APPLE, "apple-refresh")
        server.verify()
    }

    @Test fun `development app redirect exchanges and verifies with the development bundle id`() {
        var form = ""
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andExpect { form = (it as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString }
            .andRespond(withSuccess("""{"refresh_token":"dev-refresh","id_token":"${idToken(audience = "com.ssafy.eolssu.dev")}"}""", MediaType.APPLICATION_JSON))
        val identity = devGateway.identify(SocialProvider.APPLE, devRedirect, "dev-code", state, "binding")
        server.verify()
        assertEquals("001234.apple-user", identity.subject)
        val fields = formOf(form)
        assertEquals("com.ssafy.eolssu.dev", fields["client_id"])
        assertEquals("com.ssafy.eolssu.dev", SignedJWT.parse(fields["client_secret"]).jwtClaimsSet.subject)
        val query = formOf(URI(devGateway.authorizationUrl(SocialProvider.APPLE, devRedirect, state, "binding")).rawQuery)
        assertEquals("com.ssafy.eolssu.dev", query["client_id"])
    }

    @Test fun `release and development tokens are not accepted for the other app`() {
        expectToken(idToken(audience = "com.ssafy.eolssu"))
        assertFailsWith<OAuthProfileRejected> { devGateway.identify(SocialProvider.APPLE, devRedirect, "dev-code", state, "binding") }
        server.verify(); server.reset()
        var form = ""
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
            .andExpect { form = (it as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString }
            .andRespond(withSuccess("""{"id_token":"${idToken(audience = "com.ssafy.eolssu.dev")}"}""", MediaType.APPLICATION_JSON))
        assertFailsWith<OAuthProfileRejected> { devGateway.identify(SocialProvider.APPLE, redirect, "apple-code", state, "binding") }
        assertEquals("com.ssafy.eolssu", formOf(form)["client_id"])
    }

    @Test fun `development redirect is refused unless the development bundle id is configured`() {
        assertTrue(devGateway.allows(SocialProvider.APPLE, devRedirect))
        assertFalse(gateway.allows(SocialProvider.APPLE, devRedirect))
        val noBundle = AppleOAuthGateway(AppleSettings("com.ssafy.eolssu", "TEAM123456", "KEY1234567", pem, listOf(redirect), "", listOf(devRedirect)),
            builder.build(), NimbusJwtDecoder.withPublicKey(appleKeys.public as RSAPublicKey).build(), clock)
        assertFalse(noBundle.allows(SocialProvider.APPLE, devRedirect))
    }

    @Test fun `revoke retries with the development bundle id when apple refuses the release one`() {
        val clients = mutableListOf<String?>()
        repeat(2) { attempt ->
            server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andExpect { clients += formOf((it as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString)["client_id"] }
                .andRespond(if (attempt == 0) withBadRequest().body("""{"error":"invalid_client"}""") else withSuccess())
        }
        devGateway.revoke(SocialProvider.APPLE, "dev-refresh")
        server.verify()
        assertEquals(listOf<String?>("com.ssafy.eolssu", "com.ssafy.eolssu.dev"), clients)
    }

    @Test fun `revoke failure without a development app is not retried`() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke")).andRespond(withBadRequest())
        assertFailsWith<org.springframework.web.client.HttpClientErrorException> { gateway.revoke(SocialProvider.APPLE, "apple-refresh") }
        server.verify()
    }
}

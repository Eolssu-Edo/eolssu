package com.ssafy.eolssu.backend.feature.auth.application

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.*
import java.util.Date
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.kakaoSetDecoder
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.util.Resource
import com.nimbusds.jose.util.ResourceRetriever

class KakaoEventVerifierTests {
    private val now=Instant.parse("2026-10-30T00:00:00Z")
    private val key=RSAKeyGenerator(2048).keyID("test-key").generate()
    private val decoder=NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).validateType(false).build().apply {
        setJwtValidator{org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()}
    }
    private val verifier=KakaoEventVerifier(decoder,"test-client","1628175",Clock.fixed(now,ZoneOffset.UTC))

    @Test fun `signed unlink SET validates and yields typed event`() {
        val event=verifier.verify(token())
        assertEquals("12345",event.subject)
        assertEquals(now.minusSeconds(10),event.occurredAt)
        assertEquals("event-1",event.id)
    }
    @Test fun `production key source uses fixed official URL caches keys and bounds unknown kid refreshes`() {
        var requests=0
        val source=kakaoSetDecoder(ResourceRetriever { url ->
            assertEquals("https://kauth.kakao.com/.well-known/jwks.json",url.toString())
            requests++
            Resource(JWKSet(key.toPublicJWK()).toString(),"application/json")
        })
        val production=KakaoEventVerifier(source,"test-client","1628175",Clock.fixed(now,ZoneOffset.UTC))
        repeat(5){assertEquals("12345",production.verify(token()).subject)}
        assertEquals(1,requests)
        repeat(5){index ->
            val parsed=SignedJWT.parse(token())
            val unknown=SignedJWT(JWSHeader.Builder(parsed.header).keyID("unknown-$index").build(),parsed.jwtClaimsSet).apply{sign(RSASSASigner(key))}.serialize()
            assertFails{production.verify(unknown)}
        }
        assertTrue(requests<=2,"unknown kid must not trigger unlimited network requests: $requests")
        assertEquals("12345",production.verify(token()).subject)
    }
    @Test fun `remote key outage is retryable and never a validation acknowledgement`() {
        val source=kakaoSetDecoder(ResourceRetriever{throw java.io.IOException("fixture outage")})
        assertFailsWith<KakaoEventUnavailable>{KakaoEventVerifier(source,"test-client","1628175",Clock.fixed(now,ZoneOffset.UTC)).verify(token())}
    }
    @Test fun `wrong typ missing kid critical headers and missing event identity are rejected`() {
        for(header in listOf(
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("test-key").build(),
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType("secevent+jwt")).build(),
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType("secevent+jwt")).keyID("test-key").criticalParams(setOf("custom")).customParam("custom",true).build(),
        )) {
            val signed=SignedJWT(header,SignedJWT.parse(token()).jwtClaimsSet).apply{sign(RSASSASigner(key))}.serialize()
            assertFailsWith<KakaoEventFailure>{verifier.verify(signed)}
        }
        val event=mapOf(KakaoEventVerifier.UNLINK_EVENT to mapOf("subject" to mapOf("subject_type" to "iss-sub","iss" to KakaoEventVerifier.ISSUER,"sub" to "other")))
        assertFailsWith<KakaoEventFailure>{verifier.verify(token(mapOf("events" to event)))}
    }

    @Test fun `expired optional expiration is rejected even for otherwise fresh event`() {
        assertFailsWith<KakaoEventFailure>{verifier.verify(token(mapOf("iat" to Date.from(now.minusSeconds(20)),"exp" to Date.from(now.minusSeconds(1)))))}
    }
    @Test fun `signature payload issuer audience app subject event and time cannot be substituted`() {
        val wrong=RSAKeyGenerator(2048).keyID("test-key").generate()
        val parsed=SignedJWT.parse(token())
        val forged=SignedJWT(parsed.header,parsed.jwtClaimsSet).apply{sign(RSASSASigner(wrong))}.serialize()
        assertEquals("invalid_key",assertFailsWith<KakaoEventFailure>{verifier.verify(forged)}.error)
        for(change in listOf(
            mapOf("iss" to "https://attacker.test"),mapOf("aud" to "other-client"),mapOf("app_id" to "other-app"),
            mapOf("sub" to "54321"),mapOf("jti" to ""),mapOf("txm" to ""),mapOf("events" to emptyMap<String,Any>()),
            mapOf("iat" to now.plusSeconds(61).epochSecond),mapOf("toe" to now.minusSeconds(86400).epochSecond),
            mapOf("toe" to now.plusSeconds(61).epochSecond),mapOf("toe" to 1.5),
        )) assertFailsWith<KakaoEventFailure>{verifier.verify(token(change))}
        assertFailsWith<KakaoEventFailure>{verifier.verify("x".repeat(16385))}
        assertFailsWith<KakaoEventFailure>{verifier.verify("not-a-jwt")}
        assertFailsWith<KakaoEventUnavailable>{KakaoEventVerifier(decoder,"","",Clock.systemUTC()).verify(token())}
    }

    private fun token(changes:Map<String,Any> = emptyMap()):String {
        val claims=JWTClaimsSet.Builder().issuer("https://kauth.kakao.com").audience("test-client")
            .subject("12345").jwtID("event-1").issueTime(Date.from(now))
            .claim("app_id","1628175").claim("txm","transaction-1").claim("toe",now.minusSeconds(10).epochSecond)
            .claim("events",mapOf("https://schemas.openid.net/secevent/oauth/event-type/user-unlinked" to mapOf(
                "subject" to mapOf("subject_type" to "iss-sub","iss" to "https://kauth.kakao.com","sub" to "12345"),
                "reason" to "UNLINK_FROM_APPS")))
            .build()
        val changed=JWTClaimsSet.Builder(claims).apply{changes.forEach{(name,value)->claim(name,value)}}.build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType("secevent+jwt")).keyID("test-key").build(),changed)
            .apply{sign(RSASSASigner(key))}.serialize()
    }
}

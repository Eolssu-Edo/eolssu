package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.RemoteKeySourceException
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.source.JWKSetUnavailableException
import com.nimbusds.jose.jwk.source.RateLimitReachedException
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.util.Base64
import java.util.Date
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

/**
 * iOS 기본 Apple 로그인(`docs/specs/social-auth.md` Sign in with Apple).
 * 앱은 [authorizationUrl]을 열지 않고 `state`·`nonce`만 꺼내 기본 로그인 창에 넘긴다. 받은 authorizationCode를 서버가 교환하고,
 * 개발 앱(얼쑤 개발)은 전용 redirectUri로 구분해 그 번들 ID로 교환한다([AppleSettings.devClientId]).
 * id_token의 서명·발급자·대상(번들 ID)·만료와 nonce(= state의 SHA-256)를 확인한다. Apple 이메일은 확인된 이메일로 보지 않는다.
 * [decoder]는 Apple 공개키(`/auth/keys`)로 서명과 시각을 확인하는 고정 decoder다.
 */
class AppleOAuthGateway(
    private val settings: AppleSettings,
    private val client: RestClient,
    private val decoder: JwtDecoder,
    private val clock: Clock,
) : OAuthGateway {
    private val privateKey: ECPrivateKey? by lazy { parseKey(settings.privateKey) }

    override fun ready(provider: SocialProvider) = provider == SocialProvider.APPLE &&
        listOf(settings.clientId, settings.teamId, settings.keyId).all(String::isNotBlank) &&
        settings.redirectUris.any(String::isNotBlank) && privateKey != null

    override fun allows(provider: SocialProvider, redirectUri: String): Boolean {
        if (provider != SocialProvider.APPLE || (redirectUri !in settings.redirectUris && !isDevRedirect(redirectUri))) return false
        val uri = runCatching { URI(redirectUri) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null
    }

    override fun authorizationUrl(provider: SocialProvider, redirectUri: String, state: String, verifier: String): String {
        checkSettings(provider, redirectUri)
        val parameters = linkedMapOf("response_type" to "code", "client_id" to clientIdFor(redirectUri), "redirect_uri" to redirectUri,
            "scope" to "email", "state" to state, "nonce" to nonceFor(state))
        return AUTHORIZE + "?" + parameters.entries.joinToString("&") { (key, value) -> "${URLEncoder.encode(key, UTF_8)}=${URLEncoder.encode(value, UTF_8)}" }
    }

    override fun identify(provider: SocialProvider, redirectUri: String, code: String, state: String, verifier: String): OAuthIdentity {
        checkSettings(provider, redirectUri)
        // 코드는 그 코드를 받은 앱의 번들 ID로만 교환된다. 기본 로그인의 코드는 redirect_uri 없이 교환한다.
        val clientId = clientIdFor(redirectUri)
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code"); add("client_id", clientId)
            add("client_secret", clientSecret(clientId)); add("code", code)
        }
        val tokens = try {
            client.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String::class.java)
                ?: throw OAuthProfileRejected()
        } catch (failure: RestClientResponseException) {
            if (failure.statusCode.value() == 429 || failure.statusCode.is5xxServerError) throw OAuthRemoteUnavailable()
            throw OAuthProfileRejected()
        } catch (_: RestClientException) { throw OAuthRemoteUnavailable() }
        val root = try { JsonMapper.builder().build().readTree(tokens) } catch (_: Exception) { throw OAuthProfileRejected() }
        val idToken = root.path("id_token").takeIf { it.isString }?.asString() ?: throw OAuthProfileRejected()
        val refreshToken = root.path("refresh_token").takeIf { it.isString }?.asString()?.takeIf(String::isNotBlank)
        val jwt = try { decoder.decode(idToken) } catch (failure: Exception) {
            if (generateSequence(failure as Throwable?) { it.cause }.any { it is RemoteKeySourceException || it is RateLimitReachedException || it is JWKSetUnavailableException })
                throw OAuthRemoteUnavailable()
            throw OAuthProfileRejected()
        }
        if (jwt.getClaimAsString("iss") != ISSUER || clientId !in jwt.audience.orEmpty()) throw OAuthProfileRejected()
        // nonce는 이 state로 시작한 기본 로그인에서 받은 코드임을 묶는다. 없으면 받지 않는다.
        if (jwt.getClaimAsString("nonce") != nonceFor(state)) throw OAuthProfileRejected()
        val subject = jwt.subject?.takeIf { it.isNotBlank() && it.length <= 255 } ?: throw OAuthProfileRejected()
        val email = jwt.getClaimAsString("email")?.takeIf { it.isNotBlank() && it.length <= 320 }
        // Apple ID 이메일은 다른 도메인일 수 있고 릴레이 주소도 있어 추가 본인 확인 없이 기존 회원에 연결하지 않는다(사용자 결정).
        return OAuthIdentity(subject, email, false, providerToken = refreshToken)
    }

    /**
     * 토큰을 어느 앱이 받았는지 저장하지 않으므로 출시 앱 번들 ID로 먼저 철회하고,
     * Apple이 4xx(다른 앱의 토큰)로 거절하면 개발 앱 번들 ID로 한 번 더 시도한다.
     */
    override fun revoke(provider: SocialProvider, token: String) {
        if (provider != SocialProvider.APPLE) return
        if (!ready(provider)) throw OAuthRemoteUnavailable()
        try {
            revokeAs(settings.clientId, token)
        } catch (failure: RestClientResponseException) {
            if (!devReady() || !failure.statusCode.is4xxClientError) throw failure
            revokeAs(settings.devClientId, token)
        }
    }

    private fun revokeAs(clientId: String, token: String) {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId); add("client_secret", clientSecret(clientId))
            add("token", token); add("token_type_hint", "refresh_token")
        }
        client.post().uri(REVOKE).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity()
    }

    private fun devReady() = settings.devClientId.isNotBlank() && settings.devRedirectUris.any(String::isNotBlank)

    private fun isDevRedirect(redirectUri: String) = devReady() && redirectUri in settings.devRedirectUris

    /** 개발 앱 복귀 주소면 개발 앱 번들 ID, 아니면 출시 앱 번들 ID. */
    private fun clientIdFor(redirectUri: String) = if (isDevRedirect(redirectUri)) settings.devClientId else settings.clientId

    /** Apple이 요구하는 ES256 client_secret. sub는 교환·철회하는 앱의 번들 ID다. 요청마다 5분짜리로 새로 만든다. */
    private fun clientSecret(clientId: String): String {
        val now = clock.instant()
        val claims = JWTClaimsSet.Builder().issuer(settings.teamId).subject(clientId).audience(ISSUER)
            .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(settings.keyId).build(), claims)
            .apply { sign(ECDSASigner(checkNotNull(privateKey))) }.serialize()
    }

    private fun checkSettings(provider: SocialProvider, redirectUri: String) {
        if (!ready(provider)) throw OAuthRemoteUnavailable()
        if (!allows(provider, redirectUri)) throw OAuthProfileRejected()
    }

    companion object {
        const val ISSUER = "https://appleid.apple.com"
        const val AUTHORIZE = "https://appleid.apple.com/auth/authorize"
        const val TOKEN = "https://appleid.apple.com/auth/token"
        const val REVOKE = "https://appleid.apple.com/auth/revoke"
        const val KEYS = "https://appleid.apple.com/auth/keys"

        /** 앱이 기본 로그인 요청에 넣는 nonce. Apple은 받은 값을 id_token의 nonce에 그대로 담는다. */
        fun nonceFor(state: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(state.toByteArray(UTF_8)))

        /** PKCS#8 PEM(.p8). 환경 변수의 `\n` 글자도 줄바꿈으로 본다. 형식이 틀리면 null(설정 없음과 같게 503). */
        fun parseKey(pem: String): ECPrivateKey? = runCatching {
            val body = pem.replace("\\n", "\n").lines().filterNot { it.startsWith("-----") }.joinToString("").filterNot(Char::isWhitespace)
            if (body.isEmpty()) return null
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))) as ECPrivateKey
        }.getOrNull()
    }
}

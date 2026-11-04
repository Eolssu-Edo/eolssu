package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.ssafy.eolssu.backend.feature.auth.domain.*
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestClientException
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64

class RestOAuthGateway(private val properties: OAuthProviderProperties, private val client: RestClient) : OAuthGateway {
    override fun ready(provider: SocialProvider) = properties.client(provider).let {
        it.clientId.isNotBlank() && it.clientSecret.isNotBlank() && it.redirectUris.any(String::isNotBlank)
    }

    override fun allows(provider: SocialProvider, redirectUri: String): Boolean {
        if (redirectUri !in properties.client(provider).redirectUris) return false
        val uri = runCatching { URI(redirectUri) }.getOrNull() ?: return false
        return uri.host != null && uri.userInfo == null && uri.fragment == null &&
            (uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("localhost","127.0.0.1","[::1]")))
    }

    override fun authorizationUrl(provider: SocialProvider, redirectUri: String, state: String, verifier: String): String {
        checkSettings(provider,redirectUri)
        val parameters = linkedMapOf("response_type" to "code","client_id" to properties.client(provider).clientId,
            "redirect_uri" to redirectUri,"state" to state)
        if (provider == SocialProvider.GOOGLE) {
            parameters["scope"] = "openid email"
            parameters["code_challenge_method"] = "S256"
            parameters["code_challenge"] = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(UTF_8)))
        }
        val endpoint = when(provider) {
            SocialProvider.KAKAO -> "https://kauth.kakao.com/oauth/authorize"
            SocialProvider.GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth"
            SocialProvider.NAVER -> "https://nid.naver.com/oauth2.0/authorize"
            SocialProvider.APPLE -> throw OAuthRemoteUnavailable() // AppleOAuthGateway가 맡는다.
        }
        return endpoint + "?" + parameters.entries.joinToString("&") { (key,value) -> "${URLEncoder.encode(key,UTF_8)}=${URLEncoder.encode(value,UTF_8)}" }
    }

    override fun identify(provider: SocialProvider, redirectUri: String, code: String, state: String, verifier: String): OAuthIdentity {
        checkSettings(provider,redirectUri)
        val settings = properties.client(provider)
        val tokenEndpoint = when(provider) {
            SocialProvider.KAKAO -> "https://kauth.kakao.com/oauth/token"
            SocialProvider.GOOGLE -> "https://oauth2.googleapis.com/token"
            SocialProvider.NAVER -> "https://nid.naver.com/oauth2.0/token"
            SocialProvider.APPLE -> throw OAuthRemoteUnavailable()
        }
        val userEndpoint = when(provider) {
            SocialProvider.KAKAO -> "https://kapi.kakao.com/v2/user/me"
            SocialProvider.GOOGLE -> "https://openidconnect.googleapis.com/v1/userinfo"
            SocialProvider.NAVER -> "https://openapi.naver.com/v1/nid/me"
            SocialProvider.APPLE -> throw OAuthRemoteUnavailable()
        }
        val form = LinkedMultiValueMap<String,String>().apply {
            add("grant_type","authorization_code"); add("client_id",settings.clientId)
            add("client_secret",settings.clientSecret); add("redirect_uri",redirectUri); add("code",code)
            if (provider == SocialProvider.NAVER) add("state",state)
            if (provider == SocialProvider.GOOGLE) add("code_verifier",verifier)
        }
        try {
            val tokenBody = client.post().uri(tokenEndpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String::class.java)
                ?: throw OAuthProfileRejected()
            val node = JsonMapper.builder().build().readTree(tokenBody).path("access_token")
            if (!node.isString || node.asString().isBlank()) throw OAuthProfileRejected()
            val userBody = client.get().uri(userEndpoint).header("Authorization","Bearer ${node.asString()}").retrieve().body(String::class.java)
                ?: throw OAuthProfileRejected()
            return OAuthIdentityParser.parse(provider,userBody)
        } catch (failure: RestClientResponseException) {
            if (failure.statusCode.value() == 429 || failure.statusCode.is5xxServerError) throw OAuthRemoteUnavailable()
            throw OAuthProfileRejected()
        } catch (_: RestClientException) { throw OAuthRemoteUnavailable()
        } catch (_: OAuthProfileRejected) { throw OAuthProfileRejected()
        } catch (_: Exception) { throw OAuthProfileRejected() }
    }

    private fun checkSettings(provider: SocialProvider, redirectUri: String) {
        if (!ready(provider)) throw OAuthRemoteUnavailable()
        if (!allows(provider,redirectUri)) throw OAuthProfileRejected()
    }
}

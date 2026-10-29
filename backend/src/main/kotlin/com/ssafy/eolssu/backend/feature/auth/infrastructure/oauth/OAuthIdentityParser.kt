package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import com.ssafy.eolssu.backend.feature.auth.domain.OAuthIdentity
import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider

class OAuthProfileRejected : RuntimeException("OAuth profile rejected")

object OAuthIdentityParser {
    private val json = JsonMapper.builder().build()

    fun parse(provider: SocialProvider, body: String): OAuthIdentity {
        try {
            val root = json.readTree(body)
            val profile = when (provider) {
                SocialProvider.KAKAO -> root.path("kakao_account")
                SocialProvider.GOOGLE -> root
                SocialProvider.NAVER -> {
                    if (root.path("resultcode").asString() != "00") throw OAuthProfileRejected()
                    root.path("response")
                }
            }
            val subject = when (provider) {
                SocialProvider.KAKAO -> root.path("id").let {
                    if (!it.isIntegralNumber || !it.canConvertToLong() || it.asLong() <= 0) throw OAuthProfileRejected()
                    it.asLong().toString()
                }
                SocialProvider.GOOGLE -> root.path("sub").text()
                SocialProvider.NAVER -> profile.path("id").text()
            }
            if (subject.isNullOrBlank() || subject.length > 255) throw OAuthProfileRejected()
            val email = profile.path("email").text()?.takeIf { it.isNotBlank() && it.length <= 320 }
            val verified = email != null && when (provider) {
                SocialProvider.KAKAO -> profile.path("is_email_valid").trueBoolean() && profile.path("is_email_verified").trueBoolean()
                // 외부 이메일은 최초 Google 가입 때 확인했어도 현재 소유가 바뀔 수 있다.
                // Google이 관리하는 Gmail/Workspace만 추가 본인 확인 생략의 근거로 쓴다.
                SocialProvider.GOOGLE -> profile.path("email_verified").trueBoolean() &&
                    (email.endsWith("@gmail.com", ignoreCase = true) || !profile.path("hd").text().isNullOrBlank())
                SocialProvider.NAVER -> false // 이메일 확인 여부를 제공하지 않으므로 별도 본인 확인이 필요하다.
            }
            return OAuthIdentity(subject, email, verified)
        } catch (_: Exception) {
            // JSON 파싱 예외의 원문/본문과 제공자 응답을 로그·클라이언트에 전달하지 않는다.
            throw OAuthProfileRejected()
        }
    }

    private fun JsonNode.text(): String? = if (isString) asString() else null
    private fun JsonNode.trueBoolean() = isBoolean && asBoolean()
}

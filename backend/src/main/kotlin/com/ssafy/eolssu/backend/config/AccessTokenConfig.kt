package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.domain.NonExpiringSessionCheck
import org.springframework.beans.factory.ObjectProvider
import java.time.Duration
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AccessTokenConfig {
    @Bean
    fun accessTokenResolver() = BearerTokenResolver { request ->
        request.cookies?.firstOrNull { it.name == "access_token" }?.value?.takeIf { it.isNotBlank() }
    }

    @Bean
    fun accessTokenDecoder(properties: SecurityProperties, sessions: ObjectProvider<NonExpiringSessionCheck>? = null): JwtDecoder {
        // 배포 환경의 비밀값 연결 전에도 기동한다. 검증을 우회하거나 기본 키를 사용하지 않는다.
        if (properties.jwtSecret.isBlank()) return JwtDecoder { throw BadJwtException("Access token verification unavailable") }
        val key = properties.jwtSecret.toByteArray(Charsets.UTF_8)
        require(key.size >= 32) { "AUTH_JWT_SECRET must contain at least 32 UTF-8 bytes" }
        return NimbusJwtDecoder.withSecretKey(SecretKeySpec(key, "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256).build().apply {
                setJwtValidator(DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator(Duration.ZERO),
                    OAuth2TokenValidator<Jwt> { jwt ->
                        // principal을 받는 컨트롤러가 users.id로 안전하게 변환할 수 있어야 한다.
                        val userId = jwt.subject?.toLongOrNull() ?: 0
                        val sessionHash = jwt.claims["session_hash"] as? String
                        val nonExpiring = jwt.expiresAt == null && userId in properties.nonExpiringUserIds &&
                            sessionHash != null && sessionHash.matches(Regex("[a-f0-9]{64}")) &&
                            sessions?.ifAvailable?.isActive(userId, sessionHash) == true
                        if (userId > 0 && (jwt.expiresAt != null || nonExpiring)) {
                            OAuth2TokenValidatorResult.success()
                        } else {
                            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Invalid access token claims", null))
                        }
                    },
                ))
            }
    }
}

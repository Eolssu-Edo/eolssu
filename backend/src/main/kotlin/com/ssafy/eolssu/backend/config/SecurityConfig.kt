package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import com.ssafy.eolssu.backend.common.security.StateChangingRequestFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.firewall.RequestRejectedHandler
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig(
    private val properties: SecurityProperties,
    private val errorWriter: ErrorResponseWriter,
    private val accessTokenDecoder: JwtDecoder,
    private val accessTokenResolver: BearerTokenResolver,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity, paths: PathPatternRequestMatcher.Builder): SecurityFilterChain = http
        // CSRF 토큰 대신 SameSite=Lax 쿠키와 StateChangingRequestFilter로 막는다.
        .csrf { it.disable() }
        // 운영·compose는 웹과 API가 같은 주소이고 Nginx가 HTTPS를 끝내 백엔드는 http로 받는다. 이때 Spring CORS는
        // https Origin을 다른 출처로 보고 막으므로, 개발 주소를 설정했을 때만 켠다(같은 사이트 판단은 아래 필터가 한다).
        .cors { if (properties.normalizedAllowedOrigins.isEmpty()) it.disable() else it.configurationSource(corsConfigurationSource()) }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .logout { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .oauth2ResourceServer {
            it.bearerTokenResolver(accessTokenResolver)
            it.jwt { jwt -> jwt.decoder(accessTokenDecoder) }
            it.authenticationEntryPoint { _, response, _ -> errorWriter.write(response, ErrorCode.UNAUTHENTICATED) }
            it.accessDeniedHandler { _, response, _ -> errorWriter.write(response, ErrorCode.FORBIDDEN) }
        }
        // CORS 필터보다 먼저 두어, 다른 사이트의 상태 변경 요청도 공통 오류 형식(403)으로 응답한다.
        // 업로드 판정은 requestMatchers(String)와 같은 Boot 제공 Builder로 만들어 서블릿 경로 기준을 맞춘다. 인코딩된 경로도 디코딩해 비교한다.
        .addFilterBefore(
            StateChangingRequestFilter(
                properties.normalizedAllowedOrigins,
                paths.matcher(HttpMethod.POST, RECORDING_UPLOAD),
                errorWriter,
            ),
            CorsFilter::class.java,
        )
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST, *PUBLIC_POST).permitAll()
            it.requestMatchers(HttpMethod.GET, *PUBLIC_GET).permitAll()
            it.requestMatchers("/api/actuator/**").permitAll()
            it.requestMatchers("/api/**").authenticated()
            it.anyRequest().permitAll()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { _, response, _ -> errorWriter.write(response, ErrorCode.UNAUTHENTICATED) }
            it.accessDeniedHandler { _, response, _ -> errorWriter.write(response, ErrorCode.FORBIDDEN) }
        }
        .build()

    // //api·/./·;x 같은 비정상 경로는 방화벽이 거절한다. 그 응답도 공통 오류 형식으로 맞춘다.
    @Bean
    fun requestRejectedHandler() = RequestRejectedHandler { _, response, _ ->
        errorWriter.write(response, ErrorCode.INVALID_REQUEST)
    }

    // 설정한 개발 주소만 쿠키를 포함한 요청을 허용한다.
    private fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = properties.normalizedAllowedOrigins.toList()
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
            allowedHeaders = listOf("Content-Type", "Idempotency-Key")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/api/**", configuration) }
    }

    private companion object {
        // multipart 본문을 받는 유일한 상태 변경 요청.
        const val RECORDING_UPLOAD = "/api/practice-sessions/{sessionId}/recording"

        // 로그인 전에 쓰는 API(명세 Request Header '공개'). 재발급은 리프레시 토큰 쿠키로 확인한다.
        // 와일드카드를 쓰지 않고 명세 경로만 연다. 그 아래에 회원 전용 API가 생겨도 공개되지 않게 하려는 것이다.
        val PUBLIC_POST = arrayOf(
            "/api/auth/sign-in", "/api/auth/sign-up", "/api/auth/refresh", "/api/auth/password-reset",
            "/api/auth/oauth/{provider}", "/api/auth/email-verifications", "/api/auth/email-verifications/{verificationId}/confirm",
        )
        val PUBLIC_GET = arrayOf(
            "/api/terms", "/api/songs", "/api/songs/{songId}", "/api/songs/{songId}/score", "/api/songs/{songId}/model-audio",
            "/api/instruments", "/api/instruments/{instrumentId}/fingerings", "/api/pitches",
            "/api/jangdans", "/api/jangdans/{jangdanId}", "/api/notation-symbols",
        )
    }
}

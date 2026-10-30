package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.*
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import java.net.http.HttpClient
import java.time.*
import org.springframework.context.annotation.*
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods=false)
@Profile("db | ops")
class SocialAuthConfig {
    @Bean fun oauthProofRepository(jdbc:JdbcTemplate,manager:PlatformTransactionManager):OAuthProofRepository=JdbcOAuthProofRepository(jdbc,manager)
    @Bean fun oauthProofService(proofs:OAuthProofRepository,secrets:VerificationSecrets,clock:Clock)=OAuthProofService(proofs,secrets,clock)
    @Bean fun oauthGateway(properties:OAuthProviderProperties):OAuthGateway {
        val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()
        val requests=JdkClientHttpRequestFactory(http).apply{setReadTimeout(Duration.ofSeconds(5))}
        return RestOAuthGateway(properties,RestClient.builder().requestFactory(requests).build())
    }
    @Bean fun socialAuthRepository(users:UserMapper,jdbc:JdbcTemplate,manager:PlatformTransactionManager,subjects:KakaoSubjectLocks):SocialAuthRepository=MyBatisSocialAuthRepository(users,jdbc,manager,subjects)
    @Bean fun socialAuthService(repository:SocialAuthRepository,proofs:OAuthProofRepository,states:OAuthProofService,gateway:OAuthGateway,
        secrets:VerificationSecrets,issuer:AuthTokenIssuer,verification:PasswordResetRepository,passwords:PasswordEncoder,clock:Clock)=
        SocialAuthService(repository,proofs,states,gateway,secrets,issuer,verification,passwords,LoginAttemptLimiter(),clock)
    @Bean fun oauthProofCleanup(proofs:OAuthProofRepository,clock:Clock)=OAuthProofCleanup(proofs,clock)
}

class OAuthProofCleanup(private val proofs:OAuthProofRepository,private val clock:Clock) {
    /** 매분 최대500개만 짧게 지워 만료된 이메일·제공자 ID를 보관하지 않는다. */
    @Scheduled(fixedDelay=60000) fun cleanup(){proofs.deleteExpired(clock.instant())}
}

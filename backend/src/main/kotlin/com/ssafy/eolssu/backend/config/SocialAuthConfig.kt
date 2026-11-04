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
import org.springframework.web.client.RestTemplate
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration(proxyBeanMethods=false)
@Profile("db | ops")
class SocialAuthConfig {
    @Bean fun oauthProofRepository(jdbc:JdbcTemplate,manager:PlatformTransactionManager):OAuthProofRepository=JdbcOAuthProofRepository(jdbc,manager)
    @Bean fun oauthProofService(proofs:OAuthProofRepository,secrets:VerificationSecrets,clock:Clock)=OAuthProofService(proofs,secrets,clock)
    @Bean fun oauthGateway(properties:OAuthProviderProperties,clock:Clock):OAuthGateway {
        val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()
        val requests=JdkClientHttpRequestFactory(http).apply{setReadTimeout(Duration.ofSeconds(5))}
        val client=RestClient.builder().requestFactory(requests).build()
        // Apple 공개키는 jku 등 토큰 머리글이 아니라 고정 주소에서만 받는다.
        val appleKeys=NimbusJwtDecoder.withJwkSetUri(AppleOAuthGateway.KEYS).jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(RestTemplate(requests)).build()
        return ProviderRoutingOAuthGateway(RestOAuthGateway(properties,client),AppleOAuthGateway(properties.apple,client,appleKeys,clock))
    }
    /** Apple 개인키가 없으면 봉인하지 않는다(그때는 Apple 로그인 자체가 503이라 토큰도 없다). */
    private fun providerTokenCipher(properties:OAuthProviderProperties):ProviderTokenCipher?=
        properties.apple.privateKey.takeIf{AppleOAuthGateway.parseKey(it)!=null}?.let(::AesProviderTokenCipher)
    @Bean fun providerTokenRevocation(repository:SocialAuthRepository,gateway:OAuthGateway,properties:OAuthProviderProperties)=
        ProviderTokenRevocation(repository,gateway,providerTokenCipher(properties))
    @Bean fun socialAuthRepository(users:UserMapper,jdbc:JdbcTemplate,manager:PlatformTransactionManager,subjects:KakaoSubjectLocks):SocialAuthRepository=MyBatisSocialAuthRepository(users,jdbc,manager,subjects)
    @Bean fun socialAuthService(repository:SocialAuthRepository,proofs:OAuthProofRepository,states:OAuthProofService,gateway:OAuthGateway,
        secrets:VerificationSecrets,issuer:AuthTokenIssuer,verification:PasswordResetRepository,passwords:PasswordEncoder,clock:Clock,signUps:SignUpRepository,terms:com.ssafy.eolssu.backend.feature.term.domain.TermRepository,withdrawals:WithdrawalRepository,
        properties:OAuthProviderProperties,revocation:ProviderTokenRevocation)=
        SocialAuthService(repository,proofs,states,gateway,secrets,issuer,verification,passwords,LoginAttemptLimiter(),clock,signUps,terms,withdrawals,providerTokenCipher(properties),revocation)
    @Bean fun oauthProofCleanup(proofs:OAuthProofRepository,clock:Clock)=OAuthProofCleanup(proofs,clock)
}

class OAuthProofCleanup(private val proofs:OAuthProofRepository,private val clock:Clock) {
    /** 매분 최대500개만 짧게 지워 만료된 이메일·제공자 ID를 보관하지 않는다. */
    @Scheduled(fixedDelay=60000) fun cleanup(){proofs.deleteExpired(clock.instant())}
}

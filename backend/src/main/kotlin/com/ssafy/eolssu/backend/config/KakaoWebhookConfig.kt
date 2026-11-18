package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.auth.application.KakaoEventVerifier
import com.ssafy.eolssu.backend.feature.auth.application.KakaoEventDeadline
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.OAuthProviderProperties
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.kakaoSetDecoder
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.*
import java.time.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods=false)
@Profile("db | ops")
class KakaoWebhookConfig {
    @Bean(destroyMethod="close") fun kakaoEventDeadline()=KakaoEventDeadline()
    @Bean fun kakaoSubjectLocks(jdbc:JdbcTemplate)=KakaoSubjectLocks(jdbc)
    @Bean fun kakaoUnlinkStore(jdbc:JdbcTemplate,manager:PlatformTransactionManager,subjects:KakaoSubjectLocks)=KakaoUnlinkStore(jdbc,manager,subjects)
    @Bean fun kakaoEventVerifier(properties:OAuthProviderProperties,@Value("\${eolssu.auth.kakao-webhook-app-id:}") appId:String,clock:Clock):KakaoEventVerifier {
        // 별도 JwtDecoder 빈으로 노출하지 않는다. access_token HS256 검증과 분리한다.
        return KakaoEventVerifier(kakaoSetDecoder(),properties.kakao.clientId,appId,clock)
    }
    @Bean fun kakaoEventCleanup(store:KakaoUnlinkStore)=KakaoEventCleanup(store)
}
class KakaoEventCleanup(private val store:KakaoUnlinkStore) {
    @Scheduled(fixedDelay=60000) fun cleanup(){store.cleanup()}
}

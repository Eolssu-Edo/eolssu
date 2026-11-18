package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.nimbusds.jose.util.ResourceRetriever
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult

internal fun kakaoSetDecoder(retriever:ResourceRetriever = object:DefaultResourceRetriever(500,750,32768) {
    override fun openHTTPConnection(url:URL):HttpURLConnection=super.openHTTPConnection(url).apply{instanceFollowRedirects=false}
}):JwtDecoder {
    val keys=JWKSourceBuilder.create<SecurityContext>(URI("https://kauth.kakao.com/.well-known/jwks.json").toURL(),retriever)
        .cache(300000,1000).refreshAheadCache(false).rateLimited(10000).retrying(false).build()
    val processor=DefaultJWTProcessor<SecurityContext>().apply {
        jwsTypeVerifier=DefaultJOSEObjectTypeVerifier(JOSEObjectType("secevent+jwt"))
        jwsKeySelector=JWSVerificationKeySelector(JWSAlgorithm.RS256,keys)
        setJWTClaimsSetVerifier{_,_->} // SET의 필수 클레임과 시간 검증은 KakaoEventVerifier가 소유한다.
    }
    return NimbusJwtDecoder(processor).apply{setJwtValidator{OAuth2TokenValidatorResult.success()}}
}

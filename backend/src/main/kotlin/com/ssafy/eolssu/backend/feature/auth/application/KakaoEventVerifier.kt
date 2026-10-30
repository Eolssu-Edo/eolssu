package com.ssafy.eolssu.backend.feature.auth.application

import java.time.Clock
import java.time.Instant
import org.springframework.security.oauth2.jwt.JwtDecoder
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.RemoteKeySourceException
import com.nimbusds.jose.jwk.source.RateLimitReachedException
import com.nimbusds.jose.jwk.source.JWKSetUnavailableException

class KakaoUnlinkEvent(val id:String,val transactionId:String,val subject:String,val occurredAt:Instant)
class KakaoEventFailure(val error:String):RuntimeException(error)
class KakaoEventUnavailable:RuntimeException()

class KakaoEventVerifier(private val decoder:JwtDecoder,private val audience:String,private val appId:String,private val clock:Clock) {
    fun verify(token:String):KakaoUnlinkEvent {
        if(audience.isBlank() || appId.isBlank()) throw KakaoEventUnavailable()
        if(token.length !in 1..16384) invalid()
        val parsed=try{SignedJWT.parse(token)}catch(_:Exception){invalid()}
        val header=parsed.header
        if(header.algorithm!=JWSAlgorithm.RS256 || header.type?.toString()!="secevent+jwt" || header.keyID.isNullOrBlank() ||
            header.keyID.length>255 || !header.criticalParams.isNullOrEmpty()) invalid("invalid_key")
        // 검증 전 클레임은 사용하지 않는다. jku/x5u 등을 키 선택에 사용하지 않는 고정 decoder다.
        try{decoder.decode(token)}catch(e:Exception){
            if(generateSequence(e as Throwable?){it.cause}.any{it is RemoteKeySourceException || it is RateLimitReachedException || it is JWKSetUnavailableException}) throw KakaoEventUnavailable()
            invalid("invalid_key")
        }
        val claims=try{parsed.jwtClaimsSet.claims}catch(_:Exception){invalid()}
        if(claims["iss"]!=ISSUER) invalid("invalid_issuer")
        val aud=claims["aud"]
        if(aud!=audience && aud!=listOf(audience)) invalid("invalid_audience")
        if(claims["app_id"]!=appId) invalid("invalid_audience")
        val subject=claims["sub"] as? String ?: invalid()
        if(!subject.matches(Regex("[0-9]{1,30}"))) invalid()
        fun text(name:String)=(claims[name] as? String)?.takeIf{it.length in 1..255 && it.none{c->c.isISOControl()}}?:invalid()
        fun seconds(name:String):Instant {
            val value=claims[name]
            // Nimbus는 iat를 Date로 정규화하고 toe는 정수로 유지한다.
            return when(value){
                is java.util.Date -> value.toInstant()
                is Long -> runCatching{Instant.ofEpochSecond(value)}.getOrElse{invalid()}
                is Int -> Instant.ofEpochSecond(value.toLong())
                else -> invalid()
            }
        }
        val issued=seconds("iat"); val occurred=seconds("toe"); val now=clock.instant()
        if(claims.containsKey("exp") && seconds("exp")<=now) invalid()
        if(claims.containsKey("nbf") && seconds("nbf")>now.plusSeconds(60)) invalid()
        if(issued>now.plusSeconds(60) || occurred>now.plusSeconds(60) || occurred>issued.plusSeconds(60) ||
            issued<=now.minusSeconds(86400) || occurred<=now.minusSeconds(86400)) invalid()
        val events=claims["events"] as? Map<*,*>?:invalid()
        if(events.keys!=setOf(UNLINK_EVENT)) invalid()
        val event=events[UNLINK_EVENT] as? Map<*,*>?:invalid()
        val identity=event["subject"] as? Map<*,*>?:invalid()
        if(identity["subject_type"]!="iss-sub" || identity["iss"]!=ISSUER || identity["sub"]!=subject) invalid()
        return KakaoUnlinkEvent(text("jti"),text("txm"),subject,occurred)
    }
    private fun invalid(code:String="invalid_request"):Nothing=throw KakaoEventFailure(code)
    companion object {
        const val ISSUER="https://kauth.kakao.com"
        const val UNLINK_EVENT="https://schemas.openid.net/secevent/oauth/event-type/user-unlinked"
    }
}

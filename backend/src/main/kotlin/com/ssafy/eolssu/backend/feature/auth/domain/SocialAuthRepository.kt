package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

class SocialMember(val email:String?, val member:SignInMember)
class SocialLink(val userId:Long,val provider:String,val subject:String,val linkedAt:Instant,val id:Long)
/** 연결에 봉인해 둔 제공자 토큰([ProviderTokenCipher]). 원문이 아니다. */
class StoredProviderToken(val linkId:Long,val provider:String,val sealed:String)

interface SocialAuthRepository {
    fun <T:Any> inTransaction(action:()->T):T
    fun memberByEmail(email:String):SocialMember?
    fun member(userId:Long,lock:Boolean=false):SocialMember?
    fun linked(provider:String,subject:String):SocialLink?
    fun links(userId:Long):List<SocialLink>
    fun link(userId:Long,provider:String,subject:String,now:Instant,flowStartedAt:Instant?=null,providerToken:String?=null)
    /** 다시 로그인·재인증해 받은 새 토큰(봉인값)으로 바꾼다. */
    fun saveProviderToken(linkId:Long,sealed:String)
    fun providerTokens(userId:Long):List<StoredProviderToken>
    /** 해지한 토큰을 지운다. 그사이 새 토큰으로 바뀌었으면 지우지 않고 false. */
    fun clearProviderToken(linkId:Long,sealed:String):Boolean
    /** 회원 잠금 뒤 호출한다. 해제 통지와 해당 subject의 연결을 직렬화한다. */
    fun lockKakaoSubject(subject:String):Instant?
    fun unlink(userId:Long,provider:String):Boolean
    fun completeLogin(userId:Long,now:Instant)
}

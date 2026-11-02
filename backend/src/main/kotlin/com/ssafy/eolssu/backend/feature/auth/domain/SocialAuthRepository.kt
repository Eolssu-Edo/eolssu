package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

class SocialMember(val email:String?, val member:SignInMember)
class SocialLink(val userId:Long,val provider:String,val subject:String,val linkedAt:Instant,val id:Long)

interface SocialAuthRepository {
    fun <T:Any> inTransaction(action:()->T):T
    fun memberByEmail(email:String):SocialMember?
    fun member(userId:Long,lock:Boolean=false):SocialMember?
    fun linked(provider:String,subject:String):SocialLink?
    fun links(userId:Long):List<SocialLink>
    fun link(userId:Long,provider:String,subject:String,now:Instant,flowStartedAt:Instant?=null)
    /** 회원 잠금 뒤 호출한다. 해제 통지와 해당 subject의 연결을 직렬화한다. */
    fun lockKakaoSubject(subject:String):Instant?
    fun unlink(userId:Long,provider:String):Boolean
    fun completeLogin(userId:Long,now:Instant)
}

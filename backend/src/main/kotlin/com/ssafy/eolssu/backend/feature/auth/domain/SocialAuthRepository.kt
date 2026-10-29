package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

class SocialMember(val email:String, val member:SignInMember)
class SocialLink(val userId:Long,val provider:String,val subject:String,val linkedAt:Instant)

interface SocialAuthRepository {
    fun <T:Any> inTransaction(action:()->T):T
    fun memberByEmail(email:String):SocialMember?
    fun member(userId:Long,lock:Boolean=false):SocialMember?
    fun linked(provider:String,subject:String):SocialLink?
    fun links(userId:Long):List<SocialLink>
    fun link(userId:Long,provider:String,subject:String,now:Instant)
    fun unlink(userId:Long,provider:String):Boolean
    fun completeLogin(userId:Long,now:Instant)
}

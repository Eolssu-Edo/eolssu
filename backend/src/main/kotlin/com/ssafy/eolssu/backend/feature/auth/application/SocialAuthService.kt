package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.*
import java.time.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.dao.DuplicateKeyException

class SocialAuthFailure(val code:ErrorCode,val details:Any?=null):RuntimeException(code.name)
data class SocialRetryDetails(val retryAvailableAt:Instant)
class SocialStartResult(val authorizationUrl:String,val binding:String,val expiresAt:Instant)
class SocialLoginResult(val signedIn:SignInResult.SignedIn,val providers:List<String>)
class MergeAvailable(val ticket:String,val maskedEmail:String,val providerEmailVerified:Boolean,val expiresAt:Instant)

class SocialAuthService(
    private val repository:SocialAuthRepository,private val proofs:OAuthProofRepository,private val states:OAuthProofService,
    private val gateway:OAuthGateway,private val secrets:VerificationSecrets,private val issuer:AuthTokenIssuer,
    private val verification:PasswordResetRepository,private val passwords:PasswordEncoder,private val attempts:LoginAttemptLimiter,private val clock:Clock,
) {
    fun provider(value:String)=SocialProvider.entries.firstOrNull{it.name.lowercase()==value}?:fail(ErrorCode.INVALID_REQUEST)

    fun start(provider:SocialProvider,redirectUri:String,userId:Long?):SocialStartResult {
        requireProvider(provider,redirectUri)
        if(userId!=null && repository.member(userId)?.member?.status!=MemberStatus.ACTIVE) fail(ErrorCode.UNAUTHENTICATED)
        val started=states.start(provider.name,if(userId==null)"LOGIN" else "LINK",userId,redirectUri)
        return SocialStartResult(gateway.authorizationUrl(provider,redirectUri,started.state,started.binding),started.binding,started.expiresAt)
    }

    fun login(provider:SocialProvider,code:String,redirectUri:String,state:String,binding:String?):Any {
        val identity=identity(provider,code,redirectUri,state,binding,null)
        val link=repository.linked(provider.name,identity.subject)
        if(link!=null) return repository.inTransaction {
            val member=repository.member(link.userId,true)?:fail(ErrorCode.SOCIAL_ACCOUNT_NOT_REGISTERED)
            if(repository.linked(provider.name,identity.subject)?.userId!=member.member.id) fail(ErrorCode.OAUTH_VERIFICATION_FAILED)
            complete(member)
        }
        val member=identity.email?.let(repository::memberByEmail)?:fail(ErrorCode.SOCIAL_ACCOUNT_NOT_REGISTERED)
        val ticket=secrets.generateToken()
        val expiresAt=clock.instant().plusSeconds(600)
        val verified=identity.emailVerified && identity.email.equals(member.email,true)
        proofs.insert(OAuthProof(secrets.hashToken(ticket),"MERGE",provider.name,"LOGIN",secrets.hashToken(checkNotNull(binding)),
            member.member.id,null,identity.subject,member.email,verified,expiresAt))
        return MergeAvailable(ticket,mask(member.email),verified,expiresAt)
    }

    fun merge(ticket:String,bindings:Map<String,String>,password:String?,verificationToken:String?):SocialLoginResult {
        val hash=secrets.hashToken(ticket)
        val proof=proofs.find(hash)?:fail(ErrorCode.MERGE_TICKET_EXPIRED)
        val binding=bindings[proof.provider]?:fail(ErrorCode.MERGE_TICKET_EXPIRED)
        val now=clock.instant()
        if(proof.kind!="MERGE" || proof.consumedAt!=null || !now.isBefore(proof.expiresAt) || proof.bindingHash!=secrets.hashToken(binding)) fail(ErrorCode.MERGE_TICKET_EXPIRED)
        val snapshot=repository.member(checkNotNull(proof.userId))?:fail(ErrorCode.INVALID_CREDENTIALS)
        if(snapshot.email!=proof.email) fail(ErrorCode.INVALID_CREDENTIALS)
        val useEmail=!verificationToken.isNullOrBlank()
        if(!proof.emailVerified && !useEmail) {
            if(password.isNullOrBlank()) fail(ErrorCode.IDENTITY_CONFIRMATION_REQUIRED)
            attempts.lockedUntil(snapshot.member.id.toString(),now)?.let{fail(ErrorCode.TOO_MANY_REQUESTS,SocialRetryDetails(it))}
            if(!passwords.matches(password,snapshot.member.passwordHash)) {
                attempts.recordFailure(snapshot.member.id.toString(),now)?.let{fail(ErrorCode.TOO_MANY_REQUESTS,SocialRetryDetails(it))}
                fail(ErrorCode.INVALID_CREDENTIALS)
            }
        }
        val result=repository.inTransaction {
            val now=clock.instant()
            // 회원을 먼저 잠근 뒤 이메일 증명·티켓·소셜 연결·refresh 순서로 진행한다.
            val member=repository.member(snapshot.member.id,true)?:fail(ErrorCode.INVALID_CREDENTIALS)
            if(member.email!=proof.email || member.member.passwordHash!=snapshot.member.passwordHash) fail(ErrorCode.INVALID_CREDENTIALS)
            if(!proof.emailVerified && useEmail) {
                val verified=verification.findVerificationForUpdate(secrets.hashToken(checkNotNull(verificationToken)))?:fail(ErrorCode.INVALID_CREDENTIALS)
                if(verified.purpose!=VerificationPurpose.ACCOUNT_MERGE || !verified.email.equals(member.email,true) || verified.consumedAt!=null || verified.invalidatedAt!=null || verified.tokenExpiresAt?.let{now.isBefore(it)}!=true) fail(ErrorCode.INVALID_CREDENTIALS)
                verification.markConsumed(verified.id,now)
            }
            if(!proofs.consume(hash,now)) fail(ErrorCode.MERGE_TICKET_EXPIRED)
            addLink(member.member.id,proof.provider,checkNotNull(proof.subject),now)
            complete(member)
        }
        attempts.reset(snapshot.member.id.toString())
        return result
    }

    fun link(userId:Long,provider:SocialProvider,code:String,redirectUri:String,state:String,binding:String?):Instant {
        val identity=identity(provider,code,redirectUri,state,binding,userId)
        return repository.inTransaction {
            val member=repository.member(userId,true)?:fail(ErrorCode.UNAUTHENTICATED)
            if(member.member.status!=MemberStatus.ACTIVE) fail(ErrorCode.UNAUTHENTICATED)
            val now=clock.instant()
            addLink(userId,provider.name,identity.subject,now)
            now
        }
    }

    fun unlink(userId:Long,provider:SocialProvider) {
        repository.inTransaction {
            if(repository.member(userId,true)?.member?.status!=MemberStatus.ACTIVE) fail(ErrorCode.UNAUTHENTICATED)
            if(!repository.unlink(userId,provider.name)) fail(ErrorCode.RESOURCE_NOT_FOUND)
            true
        }
    }

    private fun identity(provider:SocialProvider,code:String,redirectUri:String,state:String,binding:String?,userId:Long?):OAuthIdentity {
        requireProvider(provider,redirectUri)
        if(!states.consumeState(state,binding,provider.name,if(userId==null)"LOGIN" else "LINK",userId,redirectUri)) fail(ErrorCode.OAUTH_VERIFICATION_FAILED)
        return try{gateway.identify(provider,redirectUri,code,state,checkNotNull(binding))}
        catch(_:OAuthProfileRejected){fail(ErrorCode.OAUTH_VERIFICATION_FAILED)}
        catch(_:OAuthRemoteUnavailable){fail(ErrorCode.SERVICE_UNAVAILABLE)}
    }
    private fun requireProvider(provider:SocialProvider,redirectUri:String) {
        if(!gateway.ready(provider)) fail(ErrorCode.SERVICE_UNAVAILABLE)
        if(!gateway.allows(provider,redirectUri)) fail(ErrorCode.OAUTH_VERIFICATION_FAILED)
    }
    private fun addLink(userId:Long,provider:String,subject:String,now:Instant) {
        if(repository.links(userId).any{it.provider==provider}) fail(ErrorCode.PROVIDER_ALREADY_LINKED)
        if(repository.linked(provider,subject)!=null) fail(ErrorCode.SOCIAL_ACCOUNT_IN_USE)
        try{repository.link(userId,provider,subject,now)}catch(_:DuplicateKeyException){fail(ErrorCode.SOCIAL_ACCOUNT_IN_USE)}
    }
    private fun complete(member:SocialMember):SocialLoginResult {
        val now=clock.instant()
        repository.completeLogin(member.member.id,now)
        val tokens=try{issuer.issue(member.member.id)}catch(_:TokenIssuingUnavailable){fail(ErrorCode.SERVICE_UNAVAILABLE)}
        return SocialLoginResult(SignInResult.SignedIn(member.member,member.member.status==MemberStatus.WITHDRAWAL_PENDING,tokens),listOf("EMAIL")+repository.links(member.member.id).map{it.provider})
    }
    private fun mask(email:String)=email.substringBefore('@').take(2)+"****@"+email.substringAfter('@')
    private fun fail(code:ErrorCode,details:Any?=null):Nothing=throw SocialAuthFailure(code,details)
}

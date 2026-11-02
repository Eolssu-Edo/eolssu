package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.*
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.auth.application.*
import com.ssafy.eolssu.backend.feature.user.api.LinkedProviderResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@Profile("db | ops")
class SocialAuthController(private val service:SocialAuthService,private val cookies:AuthCookies,
    @Value("\${eolssu.auth.cookie-secure:true}") private val secure:Boolean) {
    @PostMapping("/api/auth/oauth/{provider}/start")
    fun startLogin(@PathVariable provider:String,@Valid @RequestBody request:OAuthStartRequest)=start(provider,request,null)

    @PostMapping("/api/users/me/social-accounts/{provider}/start")
    fun startLink(@AuthenticationPrincipal jwt:Jwt,@PathVariable provider:String,@Valid @RequestBody request:OAuthStartRequest)=start(provider,request,checkNotNull(jwt.subject).toLong())

    private fun start(provider:String,request:OAuthStartRequest,userId:Long?)=respond {
        val result=service.start(service.provider(provider),request.redirectUri,userId)
        ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,bindingCookie(provider,result.binding))
            .body(SuccessResponse.of(HttpStatus.OK,"소셜 인증 시작",OAuthStartResult(result.authorizationUrl,result.expiresAt)))
    }

    @PostMapping("/api/auth/oauth/{provider}")
    fun login(@PathVariable provider:String,@Valid @RequestBody request:OAuthCompleteRequest,http:HttpServletRequest)=respond {
        val binding=binding(http,provider)
        when(val result=service.login(service.provider(provider),request.authorizationCode,request.redirectUri,request.state,binding)) {
            is SocialLoginResult -> signed(result,false)
            is SocialSignUpAvailable -> ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.SET_COOKIE,bindingCookie(provider,checkNotNull(binding)))
                .body(ErrorResponse.of(ErrorCode.SOCIAL_SIGN_UP_REQUIRED,SocialSignUpDetails(result.ticket,result.expiresAt)))
            is MergeAvailable -> ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.SET_COOKIE,bindingCookie(provider,checkNotNull(binding)))
                .body(ErrorResponse.of(ErrorCode.ACCOUNT_MERGE_AVAILABLE,MergeDetails(result.ticket,result.maskedEmail,result.providerEmailVerified,result.expiresAt)))
            else -> error("Unsupported social login result")
        }
    }

    @PostMapping("/api/users/me/withdrawal/oauth/{provider}/start")
    fun startWithdrawal(@AuthenticationPrincipal jwt:Jwt,@PathVariable provider:String,@Valid @RequestBody request:OAuthStartRequest)=respond {
        val result=service.startWithdrawal(checkNotNull(jwt.subject).toLong(),service.provider(provider),request.redirectUri)
        ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,bindingCookie(provider,result.binding))
            .body(SuccessResponse.of(HttpStatus.OK,"탈퇴 소셜 재인증 시작",OAuthStartResult(result.authorizationUrl,result.expiresAt)))
    }

    @PostMapping("/api/users/me/withdrawal/oauth/{provider}")
    fun confirmWithdrawal(@AuthenticationPrincipal jwt:Jwt,@PathVariable provider:String,@Valid @RequestBody request:OAuthCompleteRequest,http:HttpServletRequest)=respond {
        val result=service.confirmWithdrawal(checkNotNull(jwt.subject).toLong(),service.provider(provider),request.authorizationCode,request.redirectUri,request.state,binding(http,provider))
        ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,bindingCookie(provider,checkNotNull(binding(http,provider))))
            .body(SuccessResponse.of(HttpStatus.OK,"탈퇴 소셜 재인증 완료",WithdrawalProofResult(result.ticket,result.expiresAt)))
    }

    @PostMapping("/api/auth/oauth/sign-up")
    fun signUp(@Valid @RequestBody request:SocialSignUpRequest,http:HttpServletRequest)=respond {
        val bindings=listOf("kakao","google","naver").mapNotNull{provider->binding(http,provider)?.let{provider.uppercase() to it}}.toMap()
        val result=service.signUp(SocialSignUpCommand(request.signupTicket,request.nickname,request.agreements.map{com.ssafy.eolssu.backend.feature.auth.domain.TermAgreement(it.termId.toLong(),it.version,it.agreed)},request.instrumentId?.toLong(),request.notationMode),bindings)
        ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE,*cookies.issue(result.tokens).toTypedArray())
            .body(SuccessResponse.of(HttpStatus.CREATED,"회원가입 성공",SignedUp(result)))
    }

    @PostMapping("/api/auth/oauth/merge")
    fun merge(@Valid @RequestBody request:OAuthMergeRequest,http:HttpServletRequest)=respond {
        val bindings=listOf("kakao","google","naver").mapNotNull{provider->binding(http,provider)?.let{provider.uppercase() to it}}.toMap()
        signed(service.merge(request.mergeTicket,bindings,request.password,request.verificationToken),true)
    }

    @PostMapping("/api/users/me/social-accounts/{provider}")
    fun link(@AuthenticationPrincipal jwt:Jwt,@PathVariable provider:String,@Valid @RequestBody request:OAuthCompleteRequest,http:HttpServletRequest)=respond {
        val linkedAt=service.link(checkNotNull(jwt.subject).toLong(),service.provider(provider),request.authorizationCode,request.redirectUri,request.state,binding(http,provider))
        ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.of(HttpStatus.CREATED,"소셜 계정 연결 성공",LinkedProviderResult(provider.uppercase(),linkedAt)))
    }

    @DeleteMapping("/api/users/me/social-accounts/{provider}")
    fun unlink(@AuthenticationPrincipal jwt:Jwt,@PathVariable provider:String)=respond {
        service.unlink(checkNotNull(jwt.subject).toLong(),service.provider(provider))
        ResponseEntity.noContent().build<Void>()
    }

    private fun signed(result:SocialLoginResult,merge:Boolean):ResponseEntity<*> {
        val member=result.signedIn.member
        val body=if(merge)MergedMemberResult(member.id.toString(),member.nickname,member.instrumentId.toString(),member.notationMode,result.providers) else SignedIn(result.signedIn)
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,*cookies.issue(result.signedIn.tokens).toTypedArray())
            .body(SuccessResponse.of(HttpStatus.OK,if(merge)"계정을 하나로 합쳤어요" else "소셜 로그인 성공",body))
    }
    private fun binding(http:HttpServletRequest,provider:String):String?=http.cookies?.filter{it.name=="oauth_flow_$provider"}?.singleOrNull()?.value
    private fun bindingCookie(provider:String,binding:String)=ResponseCookie.from("oauth_flow_$provider",binding).path("/api").httpOnly(true).secure(secure).sameSite("Lax").maxAge(600).build().toString()
    private fun respond(action:()->ResponseEntity<*>):ResponseEntity<*> = try{action()}catch(failure:SocialAuthFailure){
        ResponseEntity.status(failure.code.status).body(ErrorResponse.of(failure.code,failure.details))
    }
}

class OAuthStartRequest(@field:NotBlank @field:Size(max=2048) val redirectUri:String)
class OAuthCompleteRequest(
    @field:NotBlank @field:Size(max=8192) val authorizationCode:String,
    @field:NotBlank @field:Size(max=2048) val redirectUri:String,
    @field:Pattern(regexp="[A-Za-z0-9_-]{43}") val state:String,
)
class OAuthMergeRequest(
    @field:Pattern(regexp="[A-Za-z0-9_-]{43}") val mergeTicket:String,
    @field:Size(max=256) val password:String?=null,
    @field:Size(max=128) val verificationToken:String?=null,
)
data class OAuthStartResult(val authorizationUrl:String,val expiresAt:Instant)
/** Ticket은 로그의 자동 toString으로도 노출하지 않는다. */
class MergeDetails(val mergeTicket:String,val maskedEmail:String,val providerEmailVerified:Boolean,val expiresAt:Instant)
data class MergedMemberResult(val userId:String,val nickname:String,val instrumentId:String,val notationMode:String,val linkedProviders:List<String>)

class SocialSignUpRequest(
    @field:Pattern(regexp="[A-Za-z0-9_-]{43}") val signupTicket:String,
    @field:NotBlank @field:Size(max=60) val nickname:String,
    @field:Valid val agreements:List<AgreementRequest>,
    @field:Pattern(regexp="[1-9][0-9]{0,17}") val instrumentId:String?=null,
    @field:Pattern(regexp="HANJA|HANGUL|SOLFEGE") val notationMode:String?=null,
)
class SocialSignUpDetails(val signupTicket:String,val expiresAt:Instant)

class WithdrawalProofResult(val withdrawalTicket:String,val expiresAt:Instant)

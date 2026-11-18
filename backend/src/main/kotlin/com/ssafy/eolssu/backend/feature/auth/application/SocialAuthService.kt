package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.*
import java.time.*
import com.ssafy.eolssu.backend.feature.term.domain.TermRepository
import java.time.temporal.ChronoUnit
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.dao.DuplicateKeyException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SocialAuthFailure(val code:ErrorCode,val details:Any?=null):RuntimeException(code.name)
data class SocialRetryDetails(val retryAvailableAt:Instant)
class SocialStartResult(val authorizationUrl:String,val binding:String,val expiresAt:Instant)
class SocialLoginResult(val signedIn:SignInResult.SignedIn,val providers:List<String>)
class SocialSignUpAvailable(val ticket:String,val expiresAt:Instant)
class SocialSignUpCommand(val ticket:String,val nickname:String,val agreements:List<TermAgreement>,val instrumentId:Long?=null,val notationMode:String?=null)
class MergeAvailable(val ticket:String,val maskedEmail:String,val providerEmailVerified:Boolean,val expiresAt:Instant)

class SocialAuthService(
    private val repository:SocialAuthRepository,private val proofs:OAuthProofRepository,private val states:OAuthProofService,
    private val gateway:OAuthGateway,private val secrets:VerificationSecrets,private val issuer:AuthTokenIssuer,
    private val verification:PasswordResetRepository,private val passwords:PasswordEncoder,private val attempts:LoginAttemptLimiter,private val clock:Clock,
    private val signUps:SignUpRepository?=null,private val terms:TermRepository?=null,private val withdrawals:WithdrawalRepository?=null,
    private val cipher:ProviderTokenCipher?=null,private val revocation:ProviderTokenRevocation?=null,
) {
    // 유한한 stripe로 메모리를 제한한다. Argon2는 DB 연결/행 잠금 밖에서 계산하되
    // 같은 회원의 잠금 확인·비밀번호 비교·실패 기록·성공 reset은 직렬화한다.
    private val mergeGuards=Array(256){ReentrantLock()}
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
            checkKakaoFlow(provider.name,identity.subject,identity.flowStartedAt,ErrorCode.OAUTH_VERIFICATION_FAILED)
            val current=repository.linked(provider.name,identity.subject)
            if(current?.userId!=member.member.id) fail(ErrorCode.OAUTH_VERIFICATION_FAILED)
            sealed(identity)?.let{repository.saveProviderToken(checkNotNull(current).id,it)}
            complete(member)
        }
        val member=identity.email?.let(repository::memberByEmail)
        if(member==null) {
            val ticket=secrets.generateToken()
            val expiry=clock.instant().plusSeconds(600)
            proofs.insert(OAuthProof(secrets.hashToken(ticket),"SIGNUP",provider.name,"LOGIN",secrets.hashToken(checkNotNull(binding)),
                null,null,identity.subject,identity.email,identity.emailVerified,expiry,flowStartedAt=identity.flowStartedAt,providerToken=sealed(identity)))
            return SocialSignUpAvailable(ticket,expiry)
        }
        val ticket=secrets.generateToken()
        val expiresAt=clock.instant().plusSeconds(600)
        val verified=identity.emailVerified && identity.email.equals(member.email,true)
        proofs.insert(OAuthProof(secrets.hashToken(ticket),"MERGE",provider.name,"LOGIN",secrets.hashToken(checkNotNull(binding)),
            member.member.id,null,identity.subject,checkNotNull(member.email),verified,expiresAt,flowStartedAt=identity.flowStartedAt,providerToken=sealed(identity)))
        return MergeAvailable(ticket,mask(checkNotNull(member.email)),verified,expiresAt)
    }

    fun startWithdrawal(userId:Long,provider:SocialProvider,redirectUri:String):SocialStartResult {
        requireProvider(provider,redirectUri)
        if(repository.member(userId)?.member?.status!=MemberStatus.ACTIVE) fail(ErrorCode.UNAUTHENTICATED)
        val link=repository.links(userId).firstOrNull{it.provider==provider.name}?:fail(ErrorCode.RESOURCE_NOT_FOUND)
        val flow=states.start(provider.name,"WITHDRAW",userId,redirectUri,link.id)
        return SocialStartResult(gateway.authorizationUrl(provider,redirectUri,flow.state,flow.binding),flow.binding,flow.expiresAt)
    }

    fun confirmWithdrawal(userId:Long,provider:SocialProvider,code:String,redirectUri:String,state:String,binding:String?):SocialSignUpAvailable {
        val started=proofs.find(secrets.hashToken(state))
        val identity=identity(provider,code,redirectUri,state,binding,userId,"WITHDRAW")
        return repository.inTransaction {
            if(repository.member(userId,true)?.member?.status!=MemberStatus.ACTIVE) fail(ErrorCode.UNAUTHENTICATED)
            checkKakaoFlow(provider.name,identity.subject,identity.flowStartedAt,ErrorCode.OAUTH_VERIFICATION_FAILED)
            val link=repository.linked(provider.name,identity.subject)
            if(link==null || link.userId!=userId || started?.socialAccountId!=link.id) fail(ErrorCode.OAUTH_VERIFICATION_FAILED)
            sealed(identity)?.let{repository.saveProviderToken(link.id,it)}
            val ticket=secrets.generateToken();val expiry=clock.instant().plusSeconds(600)
            proofs.insert(OAuthProof(secrets.hashToken(ticket),"WITHDRAW",provider.name,"WITHDRAW",secrets.hashToken(checkNotNull(binding)),userId,null,identity.subject,null,false,expiry,flowStartedAt=identity.flowStartedAt,socialAccountId=link.id))
            SocialSignUpAvailable(ticket,expiry)
        }
    }

    fun withdraw(userId:Long,ticket:String,bindings:Map<String,String>):WithdrawalResult.Withdrawn {
        val withdrawal=withdrawals?:fail(ErrorCode.SERVICE_UNAVAILABLE)
        val hash=secrets.hashToken(ticket)
        val proof=proofs.find(hash)?:fail(ErrorCode.WITHDRAWAL_TICKET_EXPIRED)
        val binding=bindings[proof.provider]?:fail(ErrorCode.WITHDRAWAL_TICKET_EXPIRED)
        return repository.inTransaction {
            if(repository.member(userId,true)?.member?.status!=MemberStatus.ACTIVE) fail(ErrorCode.UNAUTHENTICATED)
            checkKakaoFlow(proof.provider,proof.subject?:fail(ErrorCode.WITHDRAWAL_TICKET_EXPIRED),proof.flowStartedAt,ErrorCode.WITHDRAWAL_TICKET_EXPIRED)
            val now=clock.instant().truncatedTo(ChronoUnit.MICROS)
            if(proof.kind!="WITHDRAW" || proof.purpose!="WITHDRAW" || proof.userId!=userId || proof.bindingHash!=secrets.hashToken(binding) || proof.consumedAt!=null || !now.isBefore(proof.expiresAt)) fail(ErrorCode.WITHDRAWAL_TICKET_EXPIRED)
            val link=repository.linked(proof.provider,proof.subject)
            if(link==null || link.userId!=userId || proof.socialAccountId!=link.id || !proofs.consume(hash,now)) fail(ErrorCode.WITHDRAWAL_TICKET_EXPIRED)
            val purge=WithdrawalPolicy.purgeAt(now)
            withdrawal.markWithdrawalPending(userId,now,purge)
            withdrawal.revokeAllRefreshTokens(userId,now)
            WithdrawalResult.Withdrawn(now,purge)
        }.also{revocation?.revokeAll(userId)}
    }

    fun signUp(command:SocialSignUpCommand,bindings:Map<String,String>):SignUpResult.Created {
        val signup=signUps?:fail(ErrorCode.SERVICE_UNAVAILABLE)
        val catalog=terms?:fail(ErrorCode.SERVICE_UNAVAILABLE)
        if(command.nickname.isBlank() || command.nickname.length>NicknamePolicy.MAX_LENGTH || command.agreements.map{it.termId}.distinct().size!=command.agreements.size ||
            command.notationMode?.let{it !in setOf("HANJA","HANGUL","SOLFEGE")}==true) fail(ErrorCode.INVALID_REQUEST)
        val hash=secrets.hashToken(command.ticket)
        val proof=proofs.find(hash)?:fail(ErrorCode.SIGNUP_TICKET_EXPIRED)
        val binding=bindings[proof.provider]?:fail(ErrorCode.SIGNUP_TICKET_EXPIRED)
        fun usable(now:Instant)=proof.kind=="SIGNUP" && proof.purpose=="LOGIN" && proof.userId==null && proof.subject!=null &&
            proof.consumedAt==null && now.isBefore(proof.expiresAt) && proof.bindingHash==secrets.hashToken(binding)
        if(!usable(clock.instant())) fail(ErrorCode.SIGNUP_TICKET_EXPIRED)
        try {
            return repository.inTransaction {
                if(!usable(clock.instant())) fail(ErrorCode.SIGNUP_TICKET_EXPIRED)
                if(repository.linked(proof.provider,checkNotNull(proof.subject))!=null) fail(ErrorCode.SOCIAL_ACCOUNT_IN_USE)
                if(proof.email?.let{repository.memberByEmail(it)}!=null) fail(ErrorCode.EMAIL_ALREADY_EXISTS)
                val now=clock.instant().truncatedTo(ChronoUnit.MICROS)
                val current=catalog.findCurrent(now)
                if(current.none{it.required}) fail(ErrorCode.SERVICE_UNAVAILABLE)
                val byId=current.associateBy{it.id}
                if(command.agreements.any{byId[it.termId]?.version!=it.version} || current.filter{it.required}.any{term->command.agreements.none{it.termId==term.id && it.agreed}}) fail(ErrorCode.TERMS_AGREEMENT_REQUIRED)
                val instrument=command.instrumentId?.takeIf{signup.activeInstrumentExists(it)}
                    ?:if(command.instrumentId==null)signup.activeInstrumentIdByCode("DANSO")?:fail(ErrorCode.SERVICE_UNAVAILABLE) else fail(ErrorCode.INVALID_REQUEST)
                val notation=command.notationMode?:"HANJA"
                val email=proof.email?.takeIf{proof.emailVerified}?.lowercase(java.util.Locale.ROOT)
                val id=signup.insertUser(NewUser(email,null,command.nickname,instrument,notation),now)
                // 새 회원 INSERT 잠금 → 카카오 subject → 티켓 → 연결·토큰 순서. 실패 시 전부 롤백한다.
                checkKakaoFlow(proof.provider,checkNotNull(proof.subject),proof.flowStartedAt,ErrorCode.SIGNUP_TICKET_EXPIRED)
                val consumedAt=clock.instant()
                if(!usable(consumedAt) || !proofs.consume(hash,consumedAt)) fail(ErrorCode.SIGNUP_TICKET_EXPIRED)
                signup.insertAgreements(id,command.agreements,now)
                addLink(id,proof.provider,proof.subject,now,proof.flowStartedAt,proof.providerToken)
                repository.completeLogin(id,now)
                val tokens=issuer.issue(id)
                SignUpResult.Created(id,command.nickname,instrument,notation,now,tokens)
            }
        } catch(_:EmailAlreadyRegistered){fail(ErrorCode.EMAIL_ALREADY_EXISTS)
        } catch(_:TokenIssuingUnavailable){fail(ErrorCode.SERVICE_UNAVAILABLE)}
    }

    fun merge(ticket:String,bindings:Map<String,String>,password:String?,verificationToken:String?):SocialLoginResult {
        val owner=proofs.find(secrets.hashToken(ticket))?.takeIf{it.kind=="MERGE"}?.userId?:fail(ErrorCode.MERGE_TICKET_EXPIRED)
        return mergeGuards[(owner xor (owner ushr 32)).toInt() and 255].withLock {
            mergeLocked(ticket,bindings,password,verificationToken)
        }
    }

    private fun mergeLocked(ticket:String,bindings:Map<String,String>,password:String?,verificationToken:String?):SocialLoginResult {
        val hash=secrets.hashToken(ticket)
        val proof=proofs.find(hash)?:fail(ErrorCode.MERGE_TICKET_EXPIRED)
        // V9에서 발급한 MERGE에는 원래 STATE 시각이 없다. 가능한 가장 이른
        // 시작(STATE10분 + MERGE10분)을 사용해 배포 경계의 오래된 흐름을 보호한다.
        val flowStarted=proof.flowStartedAt?:if(proof.provider=="KAKAO")proof.expiresAt.minusSeconds(1200) else null
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
            // 회원을 먼저 잠근 뒤 이메일 증명·티켓·소셜 연결·refresh 순서로 진행한다.
            val member=repository.member(snapshot.member.id,true)?:fail(ErrorCode.INVALID_CREDENTIALS)
            if(member.email!=proof.email || member.member.passwordHash!=snapshot.member.passwordHash) fail(ErrorCode.INVALID_CREDENTIALS)
            if(!member.member.canSignInAt(clock.instant())) fail(ErrorCode.INVALID_CREDENTIALS)
            // 이메일·티켓 잠금보다 먼저 잡는다. 웹훅도 회원→subject 순서를 따른다.
            checkKakaoFlow(proof.provider,checkNotNull(proof.subject),flowStarted,ErrorCode.MERGE_TICKET_EXPIRED)
            if(!proof.emailVerified && useEmail) {
                val verified=verification.findVerificationForUpdate(secrets.hashToken(checkNotNull(verificationToken)))?:fail(ErrorCode.INVALID_CREDENTIALS)
                val now=clock.instant() // 이메일 행 잠금 대기 중에도 만료될 수 있다.
                if(verified.purpose!=VerificationPurpose.ACCOUNT_MERGE || !verified.email.equals(member.email,true) || verified.consumedAt!=null || verified.invalidatedAt!=null || verified.tokenExpiresAt?.let{now.isBefore(it)}!=true) fail(ErrorCode.INVALID_CREDENTIALS)
                verification.markConsumed(verified.id,now)
            }
            val now=clock.instant() // 회원·이메일 잠금을 모두 얻은 뒤 최신 시각으로 소비한다.
            if(!now.isBefore(proof.expiresAt)) fail(ErrorCode.MERGE_TICKET_EXPIRED)
            if(!proofs.consume(hash,now)) fail(ErrorCode.MERGE_TICKET_EXPIRED)
            addLink(member.member.id,proof.provider,checkNotNull(proof.subject),now,flowStarted,proof.providerToken)
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
            checkKakaoFlow(provider.name,identity.subject,identity.flowStartedAt,ErrorCode.OAUTH_VERIFICATION_FAILED)
            val now=clock.instant()
            addLink(userId,provider.name,identity.subject,now,identity.flowStartedAt,sealed(identity))
            now
        }
    }

    fun unlink(userId:Long,provider:SocialProvider) {
        val removed=repository.inTransaction {
            val member=repository.member(userId,true)?:fail(ErrorCode.UNAUTHENTICATED)
            if(member.member.status!=MemberStatus.ACTIVE) fail(ErrorCode.UNAUTHENTICATED)
            val links=repository.links(userId)
            if(links.none{it.provider==provider.name}) fail(ErrorCode.RESOURCE_NOT_FOUND)
            if(member.member.passwordHash==null && links.size==1) fail(ErrorCode.LAST_LOGIN_METHOD)
            val token=repository.providerTokens(userId).firstOrNull{it.provider==provider.name}
            if(!repository.unlink(userId,provider.name)) fail(ErrorCode.RESOURCE_NOT_FOUND)
            listOfNotNull(token)
        }
        // 연결을 지운 커밋 뒤 해지한다. 실패해도 해제는 유지한다.
        removed.forEach{revocation?.revoke(it.provider,it.sealed,it.linkId)}
    }

    private fun identity(provider:SocialProvider,code:String,redirectUri:String,state:String,binding:String?,userId:Long?,purpose:String=if(userId==null)"LOGIN" else "LINK"):OAuthIdentity {
        requireProvider(provider,redirectUri)
        val started=proofs.find(secrets.hashToken(state))
        if(!states.consumeState(state,binding,provider.name,purpose,userId,redirectUri)) fail(ErrorCode.OAUTH_VERIFICATION_FAILED)
        return try{
            val identity=gateway.identify(provider,redirectUri,code,state,checkNotNull(binding))
            OAuthIdentity(identity.subject,identity.email,identity.emailVerified,started?.flowStartedAt?:started?.expiresAt?.minusSeconds(600),identity.providerToken)
        }
        catch(_:OAuthProfileRejected){fail(ErrorCode.OAUTH_VERIFICATION_FAILED)}
        catch(_:OAuthRemoteUnavailable){fail(ErrorCode.SERVICE_UNAVAILABLE)}
    }
    private fun requireProvider(provider:SocialProvider,redirectUri:String) {
        if(!gateway.ready(provider)) fail(ErrorCode.SERVICE_UNAVAILABLE)
        if(!gateway.allows(provider,redirectUri)) fail(ErrorCode.OAUTH_VERIFICATION_FAILED)
    }
    private fun checkKakaoFlow(provider:String,subject:String,started:Instant?,failure:ErrorCode) {
        if(provider!="KAKAO") return
        val unlinked=repository.lockKakaoSubject(subject)?:return
        if(started==null || started<unlinked.plusSeconds(1)) fail(failure)
    }
    private fun sealed(identity:OAuthIdentity)=identity.providerToken?.let{token->cipher?.seal(token)}
    private fun addLink(userId:Long,provider:String,subject:String,now:Instant,started:Instant?,providerToken:String?=null) {
        if(repository.links(userId).any{it.provider==provider}) fail(ErrorCode.PROVIDER_ALREADY_LINKED)
        if(repository.linked(provider,subject)!=null) fail(ErrorCode.SOCIAL_ACCOUNT_IN_USE)
        try{repository.link(userId,provider,subject,now,started,providerToken)}catch(_:DuplicateKeyException){fail(ErrorCode.SOCIAL_ACCOUNT_IN_USE)}
    }
    private fun complete(member:SocialMember):SocialLoginResult {
        val now=clock.instant().truncatedTo(ChronoUnit.MICROS)
        if(!member.member.canSignInAt(now)) fail(ErrorCode.INVALID_CREDENTIALS)
        repository.completeLogin(member.member.id,now)
        val tokens=try{issuer.issue(member.member.id)}catch(_:TokenIssuingUnavailable){fail(ErrorCode.SERVICE_UNAVAILABLE)}
        return SocialLoginResult(SignInResult.SignedIn(member.member,member.member.status==MemberStatus.WITHDRAWAL_PENDING,tokens),(if(member.member.passwordHash!=null)listOf("EMAIL") else emptyList())+repository.links(member.member.id).map{it.provider})
    }
    private fun mask(email:String)=email.substringBefore('@').take(2)+"****@"+email.substringAfter('@')
    private fun fail(code:ErrorCode,details:Any?=null):Nothing=throw SocialAuthFailure(code,details)
}

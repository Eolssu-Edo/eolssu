package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.crypto.password.PasswordEncoder

class SocialAuthReviewRegressionTests {
    private val base=Instant.parse("2026-10-29T00:00:00Z")
    private val clock=MutableClock(base)
    private val secrets=EmailVerificationSecrets(null)
    private val ticket=secrets.generateToken()
    private val binding=secrets.generateToken()
    private val repository=Members()
    private val proofs=mock(OAuthProofRepository::class.java)
    private val verification=mock(PasswordResetRepository::class.java)
    private val passwordChecks=AtomicInteger()
    private fun proof(verified:Boolean)=OAuthProof(secrets.hashToken(ticket),"MERGE","GOOGLE","LOGIN",secrets.hashToken(binding),7,null,"subject","owner@example.test",verified,base.plusSeconds(600))
    private fun service(encoder:PasswordEncoder):SocialAuthService {
        val signer=object:AccessTokenSigner{override fun sign(userId:Long,issuedAt:Instant,expiresAt:Instant)="access"}
        val refresh=object:RefreshTokenRepository{override fun insert(userId:Long,tokenHash:String,expiresAt:Instant?,now:Instant)=1L}
        return SocialAuthService(repository,proofs,OAuthProofService(proofs,secrets,clock),mock(OAuthGateway::class.java),secrets,
            AuthTokenIssuer(signer,refresh,secrets,clock),verification,encoder,LoginAttemptLimiter(),clock)
    }
    private fun encoder(action:()->Boolean)=object:PasswordEncoder{
        override fun encode(rawPassword:CharSequence?)="unused"
        override fun matches(rawPassword:CharSequence?,encodedPassword:String?)=action()
    }

    @Test fun `parallel guesses cannot compare more than five passwords`() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val arrivals=CountDownLatch(8)
        val seen=ConcurrentHashMap.newKeySet<Long>()
        `when`(proofs.find(secrets.hashToken(ticket))).thenAnswer{
            if(seen.add(Thread.currentThread().threadId()))arrivals.countDown()
            proof(false)
        }
        val service=service(encoder{passwordChecks.incrementAndGet();entered.countDown();check(release.await(10,TimeUnit.SECONDS));false})
        val start=CyclicBarrier(8);val pool=Executors.newFixedThreadPool(8)
        try {
            val requests=(1..8).map{pool.submit(Callable{
                start.await(10,TimeUnit.SECONDS)
                assertFailsWith<SocialAuthFailure>{service.merge(ticket,mapOf("GOOGLE" to binding),"guess",null)}.code
            })}
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(arrivals.await(10,TimeUnit.SECONDS));release.countDown()
            val results=requests.map{it.get(15,TimeUnit.SECONDS)}
            assertEquals(5,passwordChecks.get())
            assertEquals(4,results.count{it==ErrorCode.INVALID_CREDENTIALS})
            assertEquals(4,results.count{it==ErrorCode.TOO_MANY_REQUESTS})
        }finally{release.countDown();pool.shutdownNow()}
    }

    @Test fun `ticket is rechecked after waiting for member lock`() {
        `when`(proofs.find(secrets.hashToken(ticket))).thenReturn(proof(true))
        `when`(proofs.consume(secrets.hashToken(ticket),base)).thenReturn(true)
        repository.afterLock={clock.current=base.plusSeconds(601)}
        val service=service(encoder{error("Verified email must not use password")})
        assertEquals(ErrorCode.MERGE_TICKET_EXPIRED,assertFailsWith<SocialAuthFailure>{service.merge(ticket,mapOf("GOOGLE" to binding),null,null)}.code)
        assertEquals(0,repository.links.size)
    }

    @Test fun `email verification expiry is rechecked after its row lock`() {
        `when`(proofs.find(secrets.hashToken(ticket))).thenReturn(proof(false))
        `when`(proofs.consume(secrets.hashToken(ticket),base)).thenReturn(true)
        `when`(verification.findVerificationForUpdate(secrets.hashToken("email-proof"))).thenAnswer{
            clock.current=base.plusSeconds(6)
            ConsumableVerification(9,"owner@example.test",VerificationPurpose.ACCOUNT_MERGE,base.plusSeconds(5),null,null)
        }
        val service=service(encoder{error("Email path must not use password")})
        assertEquals(ErrorCode.INVALID_CREDENTIALS,assertFailsWith<SocialAuthFailure>{service.merge(ticket,mapOf("GOOGLE" to binding),null,"email-proof")}.code)
        verify(verification,never()).markConsumed(9,base)
    }

    @Test fun `expired withdrawal cannot be restored through a verified social merge`() {
        repository.owner=SocialMember("owner@example.test",SignInMember(7,"hash","owner",1,"HANJA",MemberStatus.WITHDRAWAL_PENDING,base.minus(Duration.ofDays(30))))
        `when`(proofs.find(secrets.hashToken(ticket))).thenReturn(proof(true))
        `when`(proofs.consume(secrets.hashToken(ticket),base)).thenReturn(true)
        val service=service(encoder{error("Verified email must not use password")})
        assertEquals(ErrorCode.INVALID_CREDENTIALS,assertFailsWith<SocialAuthFailure>{service.merge(ticket,mapOf("GOOGLE" to binding),null,null)}.code)
        assertEquals(0,repository.completedLogins)
        assertTrue(repository.links.isEmpty())
        verify(proofs,never()).consume(secrets.hashToken(ticket),base)
    }

    @Test fun `withdrawal expiry is rechecked after social merge member lock`() {
        repository.owner=SocialMember("owner@example.test",SignInMember(7,"hash","owner",1,"HANJA",MemberStatus.WITHDRAWAL_PENDING,base.minus(Duration.ofDays(30)).plusSeconds(1)))
        `when`(proofs.find(secrets.hashToken(ticket))).thenReturn(proof(true))
        `when`(proofs.consume(secrets.hashToken(ticket),base.plusSeconds(1))).thenReturn(true)
        repository.afterLock={clock.current=base.plusSeconds(1)}
        val service=service(encoder{error("Verified email must not use password")})
        assertEquals(ErrorCode.INVALID_CREDENTIALS,assertFailsWith<SocialAuthFailure>{service.merge(ticket,mapOf("GOOGLE" to binding),null,null)}.code)
        assertEquals(0,repository.completedLogins)
        assertTrue(repository.links.isEmpty())
    }

    private class MutableClock(var current:Instant):Clock(){
        override fun instant()=current
        override fun getZone()=ZoneOffset.UTC
        override fun withZone(zone:ZoneId)=this
    }
    private inner class Members:SocialAuthRepository {
        var afterLock:()->Unit={}
        val links=mutableListOf<SocialLink>()
        var owner=SocialMember("owner@example.test",SignInMember(7,"hash","owner",1,"HANJA",MemberStatus.ACTIVE))
        var completedLogins=0
        override fun <T:Any> inTransaction(action:()->T)=action()
        override fun memberByEmail(email:String)=owner
        override fun member(userId:Long,lock:Boolean):SocialMember{if(lock)afterLock();return owner}
        override fun linked(provider:String,subject:String)=links.firstOrNull()
        override fun links(userId:Long)=links.toList()
        override fun link(userId:Long,provider:String,subject:String,now:Instant){links.add(SocialLink(userId,provider,subject,now))}
        override fun unlink(userId:Long,provider:String)=false
        override fun completeLogin(userId:Long,now:Instant){completedLogins++}
    }
}

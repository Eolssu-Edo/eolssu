package com.ssafy.eolssu.backend.feature.auth

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.*
import com.ssafy.eolssu.backend.feature.auth.application.*
import java.net.URI
import java.net.http.*
import java.time.*
import java.util.Date
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.*
import org.springframework.boot.test.context.*
import org.springframework.context.annotation.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.test.context.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.KakaoUnlinkStore
import com.ssafy.eolssu.backend.feature.auth.domain.*
import java.util.concurrent.*

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(KakaoWebhookDatabaseTests.Fixtures::class)
class KakaoWebhookDatabaseTests {
    @Value("\${local.server.port}") private var port=0
    @Autowired private lateinit var jdbc:JdbcTemplate
    @Autowired private lateinit var social:SocialAuthService
    @Autowired private lateinit var secrets:com.ssafy.eolssu.backend.feature.auth.domain.VerificationSecrets
    @Autowired private lateinit var manager:PlatformTransactionManager
    @Autowired private lateinit var store:KakaoUnlinkStore
    @BeforeEach fun seed() {
        cleanup()
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,status) VALUES(990296,'webhook296@example.test','unchanged-password','웹훅',1,'ACTIVE')")
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id,created_at) VALUES(990296,'KAKAO','990296',?)",utc(Instant.now().minusSeconds(300)))
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES(990296,'GOOGLE','google296')")
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES(990296,?,?)","c".repeat(64),utc(Instant.now().plusSeconds(3600)))
        jdbc.update("INSERT INTO practice_sessions(id,user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,status,started_at) VALUES(990296,990296,1,1,90,'DOTTED_QUARTER',0,1,'RECORDING',UTC_TIMESTAMP(6))")
    }
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM practice_sessions WHERE user_id=990296")
        jdbc.update("DELETE FROM kakao_event_receipts")
        jdbc.update("DELETE FROM kakao_unlink_subjects")
        jdbc.update("DELETE FROM user_social_accounts WHERE user_id=990296")
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id=990296")
        jdbc.update("DELETE FROM oauth_proofs WHERE user_id=990296")
        jdbc.update("DELETE FROM users WHERE id=990296")
    }
    @Test fun `signed HTTP event removes only kakao and accepts duplicate with empty 202`() {
        val token=token()
        val response=send(token,cookie="access_token=expired-or-invalid")
        assertEquals(202,response.statusCode(),response.body())
        assertEquals("",response.body())
        assertEquals(listOf("GOOGLE"),jdbc.queryForList("SELECT provider FROM user_social_accounts WHERE user_id=990296",String::class.java))
        assertEquals("unchanged-password",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=990296",String::class.java))
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM refresh_tokens WHERE user_id=990296",LocalDateTime::class.java))
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM practice_sessions WHERE user_id=990296",Int::class.java))
        assertEquals(202,send(token).statusCode())
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_event_receipts",Int::class.java))
    }
    @Test fun `unlink invalidates an unfinished merge from before event without consuming ticket`() {
        val at=Instant.now().minusSeconds(10)
        assertEquals(202,send(token(at)).statusCode())
        jdbc.update("""INSERT INTO oauth_proofs(token_hash,kind,provider,purpose,binding_hash,user_id,provider_subject,email,email_verified,expires_at,flow_started_at)
            VALUES (?,'MERGE','KAKAO','LOGIN',?,990296,'990296','webhook296@example.test',TRUE,?,?)""",
            secrets.hashToken("old-ticket"),secrets.hashToken("binding"),utc(Instant.now().plusSeconds(600)),utc(at.minusSeconds(60)))
        val failure=assertFailsWith<SocialAuthFailure>{social.merge("old-ticket",mapOf("KAKAO" to "binding"),null,null)}
        assertEquals(com.ssafy.eolssu.backend.common.error.ErrorCode.MERGE_TICKET_EXPIRED,failure.code)
        assertNull(jdbc.queryForObject("SELECT consumed_at FROM oauth_proofs WHERE token_hash=?",LocalDateTime::class.java,secrets.hashToken("old-ticket")))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE provider='KAKAO' AND user_id=990296",Int::class.java))
    }
    @Test fun `late first event preserves a new connection and older flow completed late is removed`() {
        val at=Instant.now().minusSeconds(60)
        jdbc.update("UPDATE user_social_accounts SET oauth_started_at=?,created_at=? WHERE user_id=990296 AND provider='KAKAO'",utc(at.plusSeconds(10)),utc(at.plusSeconds(20)))
        assertEquals(202,send(token(at)).statusCode())
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE provider='KAKAO' AND user_id=990296",Int::class.java))
        // 완료 시각이 해제보다 늦어도, 그 흐름은 해제 전에 시작됐으므로 무효다.
        jdbc.update("UPDATE user_social_accounts SET oauth_started_at=? WHERE user_id=990296 AND provider='KAKAO'",utc(at.minusSeconds(10)))
        assertEquals(202,send(token(at.plusSeconds(1))).statusCode())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE provider='KAKAO' AND user_id=990296",Int::class.java))
    }
    @Test fun `bad content signatures and unrelated public paths cannot mutate links`() {
        for((body,type) in listOf("not-jwt" to "application/secevent+jwt",token() to "application/json","x".repeat(16385) to "application/secevent+jwt")) {
            val response=send(body,type=type)
            assertEquals(400,response.statusCode(),response.body())
            assertTrue(response.body().contains("\"err\""))
            assertFalse(response.body().contains("result"))
        }
        assertEquals(401,send(token(),path="/api/webhooks/kakao/other",type="application/json").statusCode())
        assertEquals(415,send(token(),path="/api/auth/sign-in").statusCode())
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE provider='KAKAO' AND user_id=990296",Int::class.java))
    }
    @Test fun `old state completion is rejected and fresh flow can relink after unlink`() {
        val at=Instant.now().minusSeconds(10)
        val old=social.start(SocialProvider.KAKAO,"https://example.test/callback",990296)
        jdbc.update("UPDATE oauth_proofs SET flow_started_at=? WHERE token_hash=?",utc(at.minusSeconds(10)),secrets.hashToken(old.authorizationUrl.substringAfter("state=")))
        assertEquals(202,send(token(at)).statusCode())
        assertEquals(com.ssafy.eolssu.backend.common.error.ErrorCode.OAUTH_VERIFICATION_FAILED,
            assertFailsWith<SocialAuthFailure>{social.link(990296,SocialProvider.KAKAO,"code","https://example.test/callback",old.authorizationUrl.substringAfter("state="),old.binding)}.code)
        val fresh=social.start(SocialProvider.KAKAO,"https://example.test/callback",990296)
        social.link(990296,SocialProvider.KAKAO,"code","https://example.test/callback",fresh.authorizationUrl.substringAfter("state="),fresh.binding)
        assertNotNull(jdbc.queryForObject("SELECT oauth_started_at FROM user_social_accounts WHERE user_id=990296 AND provider='KAKAO'",LocalDateTime::class.java))
        assertEquals(202,send(token(at)).statusCode())
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=990296 AND provider='KAKAO'",Int::class.java))
    }
    @Test fun `legacy pending merge completed before delayed delivery still retains original flow boundary`() {
        val at=Instant.now().minusSeconds(10)
        jdbc.update("DELETE FROM user_social_accounts WHERE user_id=990296 AND provider='KAKAO'")
        jdbc.update("""INSERT INTO oauth_proofs(token_hash,kind,provider,purpose,binding_hash,user_id,provider_subject,email,email_verified,expires_at)
            VALUES (?,'MERGE','KAKAO','LOGIN',?,990296,'990296','webhook296@example.test',TRUE,?)""",
            secrets.hashToken("legacy-ticket"),secrets.hashToken("binding"),utc(Instant.now().plusSeconds(600)))
        social.merge("legacy-ticket",mapOf("KAKAO" to "binding"),null,null)
        assertEquals(202,send(token(at)).statusCode())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=990296 AND provider='KAKAO'",Int::class.java))
    }
    @Test fun `duplicate transaction id and simultaneous delivery produce one receipt`() {
        val signed=token(id="delivery-one",txm="same-transaction")
        val pool=Executors.newFixedThreadPool(2)
        try {
            val start=CountDownLatch(1)
            val results=(1..2).map{pool.submit(Callable{start.await();send(signed).statusCode()})}
            start.countDown()
            assertEquals(listOf(202,202),results.map{it.get(5,TimeUnit.SECONDS)})
            assertEquals(202,send(token(id="delivery-two",txm="same-transaction")).statusCode())
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_event_receipts",Int::class.java))
        } finally {pool.shutdownNow()}
    }
    @Test fun `transaction rollback leaves link receipt and watermark unchanged`() {
        assertFailsWith<IllegalStateException>{TransactionTemplate(manager).executeWithoutResult {
            store.accept(KakaoUnlinkEvent("rollback","rollback-tx","990296",Instant.now().minusSeconds(10)))
            error("forced local transaction rollback")
        }}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=990296 AND provider='KAKAO'",Int::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_event_receipts",Int::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_unlink_subjects",Int::class.java))
    }
    @Test fun `member lock contention returns retryable failure without partial receipt`() {
        val locked=CountDownLatch(1); val release=CountDownLatch(1)
        val pool=Executors.newSingleThreadExecutor()
        val holder=pool.submit{TransactionTemplate(manager).executeWithoutResult {
            jdbc.queryForList("SELECT id FROM users WHERE id=990296 FOR UPDATE")
            locked.countDown(); check(release.await(10,TimeUnit.SECONDS))
        }}
        try {
            assertTrue(locked.await(5,TimeUnit.SECONDS))
            val begin=System.nanoTime(); val result=send(token())
            assertEquals(503,result.statusCode(),result.body())
            assertTrue(Duration.ofNanos(System.nanoTime()-begin)<Duration.ofSeconds(3))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_event_receipts",Int::class.java))
        } finally {release.countDown();holder.get(5,TimeUnit.SECONDS);pool.shutdownNow()}
    }
    @Test fun `withdrawal pending member keeps status sessions and other provider`() {
        jdbc.update("UPDATE users SET status='WITHDRAWAL_PENDING',withdrawal_requested_at=UTC_TIMESTAMP(6),purge_scheduled_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 DAY) WHERE id=990296")
        assertEquals(202,send(token()).statusCode())
        assertEquals("WITHDRAWAL_PENDING",jdbc.queryForObject("SELECT status FROM users WHERE id=990296",String::class.java))
        assertNull(jdbc.queryForObject("SELECT revoked_at FROM refresh_tokens WHERE user_id=990296",LocalDateTime::class.java))
        assertEquals(listOf("GOOGLE"),jdbc.queryForList("SELECT provider FROM user_social_accounts WHERE user_id=990296",String::class.java))
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM practice_sessions WHERE user_id=990296",Int::class.java))
    }
    @Test fun `same second unlink removes ambiguous link and rejects pending merge for restart`() {
        val at=Instant.now().minusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        jdbc.update("UPDATE user_social_accounts SET oauth_started_at=? WHERE user_id=990296 AND provider='KAKAO'",utc(at.plusMillis(500)))
        assertEquals(202,send(token(at)).statusCode())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=990296 AND provider='KAKAO'",Int::class.java))
        jdbc.update("""INSERT INTO oauth_proofs(token_hash,kind,provider,purpose,binding_hash,user_id,provider_subject,email,email_verified,expires_at,flow_started_at)
            VALUES (?,'MERGE','KAKAO','LOGIN',?,990296,'990296','webhook296@example.test',TRUE,?,?)""",
            secrets.hashToken("same-second"),secrets.hashToken("binding"),utc(Instant.now().plusSeconds(600)),utc(at.plusMillis(500)))
        assertEquals(com.ssafy.eolssu.backend.common.error.ErrorCode.MERGE_TICKET_EXPIRED,
            assertFailsWith<SocialAuthFailure>{social.merge("same-second",mapOf("KAKAO" to "binding"),null,null)}.code)
    }
    @Test fun `cleanup removes at most 500 expired receipts and subject hashes and preserves recent rows`() {
        for(index in 1..501) {
            val hash=secrets.hashToken("expired-$index")
            jdbc.update("INSERT INTO kakao_event_receipts(event_hash,transaction_hash,received_at) VALUES(?,?,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 49 HOUR))",hash,hash)
            jdbc.update("INSERT INTO kakao_unlink_subjects(subject_hash,touched_at) VALUES(?,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 49 HOUR))",hash)
        }
        assertEquals(202,send(token()).statusCode())
        store.cleanup()
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_event_receipts",Int::class.java))
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_unlink_subjects",Int::class.java))
        store.cleanup()
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_event_receipts",Int::class.java))
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM kakao_unlink_subjects",Int::class.java))
    }
    private fun send(token:String,cookie:String?=null,type:String="application/secevent+jwt",path:String="/api/webhooks/kakao/events"):HttpResponse<String> = HttpClient.newHttpClient().use { client ->
        val request=HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type",type)
        if(cookie!=null)request.header("Cookie",cookie)
        client.send(request.POST(HttpRequest.BodyPublishers.ofString(token)).build(),HttpResponse.BodyHandlers.ofString())
    }
    private fun token(at:Instant=Instant.now().minusSeconds(10),id:String=UUID.randomUUID().toString(),txm:String=UUID.randomUUID().toString()):String {
        val claims=JWTClaimsSet.Builder().issuer(KakaoEventVerifier.ISSUER).audience("test-client").subject("990296")
            .jwtID(id).issueTime(Date.from(Instant.now())).claim("app_id","1628175").claim("txm",txm).claim("toe",at.epochSecond)
            .claim("events",mapOf(KakaoEventVerifier.UNLINK_EVENT to mapOf("subject" to mapOf("subject_type" to "iss-sub","iss" to KakaoEventVerifier.ISSUER,"sub" to "990296"),"reason" to "UNLINK_FROM_APPS"))).build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType("secevent+jwt")).keyID("fixture-key").build(),claims).apply{sign(RSASSASigner(key))}.serialize()
    }
    private fun utc(value:Instant)=LocalDateTime.ofInstant(value,ZoneOffset.UTC)
    @TestConfiguration(proxyBeanMethods=false)
    class Fixtures {
        @Bean @Primary fun fixtureOAuthGateway()=object:OAuthGateway {
            override fun ready(provider:SocialProvider)=true
            override fun allows(provider:SocialProvider,redirectUri:String)=redirectUri=="https://example.test/callback"
            override fun authorizationUrl(provider:SocialProvider,redirectUri:String,state:String,verifier:String)="https://provider.example.test/?state=$state"
            override fun identify(provider:SocialProvider,redirectUri:String,code:String,state:String,verifier:String)=OAuthIdentity("990296","webhook296@example.test",true)
        }
        @Bean @Primary fun fixtureKakaoVerifier()=KakaoEventVerifier(
            NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).validateType(false).build().apply{setJwtValidator{org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()}},
            "test-client","1628175",Clock.systemUTC())
    }
    companion object {
        val key=RSAKeyGenerator(2048).keyID("fixture-key").generate()
        @JvmStatic @DynamicPropertySource fun database(registry:DynamicPropertyRegistry) {
            registry.add("spring.datasource.url"){System.getenv("TEST_DB_URL")?:error("TEST_DB_URL required")}
            registry.add("spring.datasource.username"){System.getenv("TEST_DB_USERNAME")?:"root"}
            registry.add("spring.datasource.password"){System.getenv("TEST_DB_PASSWORD")?:error("TEST_DB_PASSWORD required")}
            registry.add("eolssu.security.jwt-secret"){"test-jwt-secret-for-task296-32bytes-minimum"}
            registry.add("eolssu.scheduling.enabled"){"false"}
            registry.add("eolssu.auth.mail-mode"){"memory"}
        }
    }
}

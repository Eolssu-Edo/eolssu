package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.feature.auth.domain.OAuthGateway
import com.ssafy.eolssu.backend.feature.auth.domain.OAuthIdentity
import com.ssafy.eolssu.backend.feature.auth.domain.SocialProvider
import com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth.*
import java.net.URI
import java.net.URLDecoder
import java.net.http.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import com.ssafy.eolssu.backend.feature.auth.domain.AccessTokenSigner
import java.time.Instant
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationSecrets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*
import org.springframework.test.context.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SocialAuthApiDatabaseTests.Fixtures::class)
class SocialAuthApiDatabaseTests {
    @Value("\${local.server.port}") private var port=0
    private val json=JsonMapper.builder().build()
    @Autowired private lateinit var jdbc:JdbcTemplate
    @Autowired private lateinit var passwords:PasswordEncoder
    @Autowired private lateinit var signer:AccessTokenSigner
    @Autowired private lateinit var secrets:VerificationSecrets
    @Autowired private lateinit var clock:VerificationTestClock
    @Autowired private lateinit var transactionManager:PlatformTransactionManager
    @BeforeEach fun seed() {
        cleanup()
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,status) VALUES (990211,'social211@example.test',?,'소셜',1,'ACTIVE')",passwords.encode("ExamplePassword!234"))
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,status) VALUES (990212,'other211@example.test',?,'다른회원',1,'ACTIVE')",passwords.encode("OtherPassword!234"))
    }
    @AfterEach fun cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS oauth356_fail_refresh")
        jdbc.update("DELETE FROM email_verifications WHERE email LIKE 'fresh356-%@example.test'")
        jdbc.update("DELETE FROM oauth_proofs WHERE provider_subject LIKE 'fresh356-%'")
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE nickname='소셜신규')")
        jdbc.update("DELETE FROM user_term_agreements WHERE user_id IN (SELECT id FROM users WHERE nickname='소셜신규')")
        jdbc.update("DELETE FROM user_social_accounts WHERE user_id IN (SELECT id FROM users WHERE nickname='소셜신규')")
        jdbc.update("DELETE FROM users WHERE nickname='소셜신규'")
        jdbc.update("DELETE FROM user_term_agreements WHERE term_id=990356")
        jdbc.update("DELETE FROM terms WHERE id=990356")
        jdbc.update("DELETE FROM oauth_proofs WHERE kind='SIGNUP' AND provider_subject='unknown-subject'")
        jdbc.update("DELETE FROM oauth_proofs WHERE user_id IN(990211,990212) OR redirect_uri='https://example.test/callback'")
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN(990211,990212)")
        jdbc.update("DELETE FROM email_verifications WHERE email='social211@example.test'")
        jdbc.update("DELETE FROM user_social_accounts WHERE user_id IN(990211,990212)")
        jdbc.update("DELETE FROM users WHERE id IN(990211,990212)")
    }

    private fun send(path:String,body:String?=null,cookie:String?=null,method:String=if(body==null)"GET" else "POST"):HttpResponse<String> {
        HttpClient.newHttpClient().use { client ->
            val request=HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type","application/json")
            if(cookie!=null)request.header("Cookie",cookie)
            return client.send(request.method(method,body?.let(HttpRequest.BodyPublishers::ofString)?:HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString())
        }
    }
    private fun accessCookie(userId:Long=990211):String {
        val now=Instant.now()
        return "access_token=${signer.sign(userId,now,now.plusSeconds(1800))}"
    }
    private fun started(link:Boolean=false):Pair<String,String> {
        val result=send(if(link)"/api/users/me/social-accounts/google/start" else "/api/auth/oauth/google/start",
            """{"redirectUri":"https://example.test/callback"}""",if(link)accessCookie() else null)
        assertEquals(200,result.statusCode(),result.body())
        val cookie=result.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
        val state=URLDecoder.decode(URI(json.readTree(result.body()).path("result").path("authorizationUrl").asString()).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
        return state to cookie
    }
    private fun complete(code:String,flow:Pair<String,String> = started(),link:Boolean=false):HttpResponse<String> = send(
        if(link)"/api/users/me/social-accounts/google" else "/api/auth/oauth/google",
        """{"authorizationCode":"$code","redirectUri":"https://example.test/callback","state":"${flow.first}"}""",
        flow.second+(if(link)"; ${accessCookie()}" else ""))
    private fun error(response:HttpResponse<String>)=json.readTree(response.body()).path("result").path("errorCode").asString()
    private fun ticket(response:HttpResponse<String>):String {
        assertEquals(409,response.statusCode(),response.body())
        return json.readTree(response.body()).path("result").path("details").path("mergeTicket").asString()
    }

    private fun appleFlow(link:Boolean):Pair<String,String> {
        val result=send(if(link)"/api/users/me/social-accounts/apple/start" else "/api/auth/oauth/apple/start",
            """{"redirectUri":"https://example.test/callback"}""",if(link)accessCookie() else null)
        assertEquals(200,result.statusCode(),result.body())
        val cookie=result.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
        val state=URLDecoder.decode(URI(json.readTree(result.body()).path("result").path("authorizationUrl").asString()).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
        return state to cookie
    }
    private fun appleToken()=jdbc.queryForList("SELECT provider_token FROM user_social_accounts WHERE user_id=990211 AND provider='APPLE'",String::class.java).map{checkNotNull(it)}

    @Test fun `apple link keeps a sealed token and unlink revokes it`() {
        revoked.clear()
        val flow=appleFlow(true)
        val linked=send("/api/users/me/social-accounts/apple","""{"authorizationCode":"apple","redirectUri":"https://example.test/callback","state":"${flow.first}"}""",flow.second+"; "+accessCookie())
        assertEquals(201,linked.statusCode(),linked.body())
        val stored=appleToken().single()
        assertTrue(stored.startsWith("v1.") && !stored.contains("apple-refresh"))
        assertEquals(204,send("/api/users/me/social-accounts/apple",cookie=accessCookie(),method="DELETE").statusCode())
        assertTrue(appleToken().isEmpty())
        assertEquals(listOf("apple-refresh"),revoked.toList())
    }

    @Test fun `apple reauthentication withdraws with its ticket`() {
        val flow=appleFlow(true)
        val linked=send("/api/users/me/social-accounts/apple","""{"authorizationCode":"apple","redirectUri":"https://example.test/callback","state":"${flow.first}"}""",flow.second+"; "+accessCookie())
        assertEquals(201,linked.statusCode(),linked.body())
        val started=send("/api/users/me/withdrawal/oauth/apple/start","""{"redirectUri":"https://example.test/callback"}""",accessCookie())
        assertEquals(200,started.statusCode(),started.body())
        val binding=started.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
        val url=json.readTree(started.body()).path("result").path("authorizationUrl").asString()
        val state=URLDecoder.decode(URI(url).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
        val confirmed=send("/api/users/me/withdrawal/oauth/apple","""{"authorizationCode":"apple","redirectUri":"https://example.test/callback","state":"$state"}""","${accessCookie()}; $binding")
        assertEquals(200,confirmed.statusCode(),confirmed.body())
        val ticket=json.readTree(confirmed.body()).path("result").path("withdrawalTicket").asString()
        val withdrawn=send("/api/users/me","""{"withdrawalTicket":"$ticket"}""","${accessCookie()}; $binding","DELETE")
        assertEquals(200,withdrawn.statusCode(),withdrawn.body())
        assertEquals("WITHDRAWAL_PENDING",jdbc.queryForObject("SELECT status FROM users WHERE id=990211",String::class.java))
    }

    @Test fun `apple merge carries the token through its ticket and password withdrawal revokes it`() {
        revoked.clear()
        val flow=appleFlow(false)
        val response=send("/api/auth/oauth/apple","""{"authorizationCode":"apple-merge","redirectUri":"https://example.test/callback","state":"${flow.first}"}""",flow.second)
        val details=json.readTree(response.body()).path("result").path("details")
        assertEquals(409,response.statusCode(),response.body())
        assertFalse(details.path("providerEmailVerified").asBoolean())
        val merged=send("/api/auth/oauth/merge","""{"mergeTicket":"${details.path("mergeTicket").asString()}","password":"ExamplePassword!234"}""",flow.second)
        assertEquals(200,merged.statusCode(),merged.body())
        assertTrue(appleToken().single().startsWith("v1."))
        val withdrawn=send("/api/users/me","""{"currentPassword":"ExamplePassword!234"}""",accessCookie(),method="DELETE")
        assertTrue(withdrawn.statusCode() in 200..299,withdrawn.body())
        assertEquals(listOf("apple-merge-refresh"),revoked.toList())
        assertEquals(listOf<String?>(null),jdbc.queryForList("SELECT provider_token FROM user_social_accounts WHERE user_id=990211 AND provider='APPLE'",String::class.java))
    }

    @Test fun `linked login restores withdrawal pending member and issues existing login cookies`() {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990211,'GOOGLE','existing')")
        jdbc.update("UPDATE users SET status='WITHDRAWAL_PENDING',withdrawal_requested_at=CURRENT_TIMESTAMP,purge_scheduled_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 30 DAY) WHERE id=990211")
        val result=complete("linked")
        assertEquals(200,result.statusCode(),result.body())
        val member=json.readTree(result.body()).path("result")
        assertEquals("990211",member.path("userId").asString())
        assertTrue(member.path("withdrawalCancelled").asBoolean())
        assertEquals(2,result.headers().allValues("Set-Cookie").size)
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=990211",String::class.java))
    }

    @Test fun `linked social login only restores withdrawal before thirty days`() {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990211,'GOOGLE','existing')")
        val expiry=clock.instant()
        for(offsetMicros in listOf(-1L,0L,1L)) {
            pendingSince(expiry.minus(Duration.ofDays(30)),expiry.plusSeconds(3600))
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=990211")
            clock.set(expiry.plusNanos(offsetMicros*1000))
            val result=complete("linked")
            if(offsetMicros<0) {
                assertEquals(200,result.statusCode(),result.body())
                assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=990211",String::class.java))
            } else {
                assertEquals(401,result.statusCode(),result.body())
                assertEquals("INVALID_CREDENTIALS",error(result))
                assertTrue(result.headers().allValues("Set-Cookie").isEmpty())
                assertPendingWithoutLogin()
            }
        }
    }

    @Test fun `expired withdrawal cannot be restored by social merge and ticket and links stay unchanged`() {
        val flow=started()
        val mergeTicket=ticket(complete("verified",flow))
        val expiry=clock.instant()
        pendingSince(expiry.minus(Duration.ofDays(30)),expiry.plusSeconds(3600))
        val result=send("/api/auth/oauth/merge","""{"mergeTicket":"$mergeTicket"}""",flow.second)
        assertEquals(401,result.statusCode(),result.body())
        assertEquals("INVALID_CREDENTIALS",error(result))
        assertPendingWithoutLogin()
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=990211",Int::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs WHERE token_hash=? AND consumed_at IS NOT NULL",Int::class.java,secrets.hashToken(mergeTicket)))
    }

    @Test fun `linked social login waiting for the member lock cannot cancel at expiry`() {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990211,'GOOGLE','existing')")
        val expiry=clock.instant()
        pendingSince(expiry.minus(Duration.ofDays(30)),expiry.plusSeconds(3600))
        clock.set(expiry.minusNanos(1000))
        val flow=started()
        lateinit var signingIn:CompletableFuture<HttpResponse<String>>
        TransactionTemplate(transactionManager).executeWithoutResult {
            jdbc.queryForObject("SELECT id FROM users WHERE id=990211 FOR UPDATE",Long::class.java)
            signingIn=CompletableFuture.supplyAsync{complete("linked",flow)}
            val deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos()
            var waiting=false
            while(System.nanoTime()<deadline) {
                waiting=jdbc.queryForObject(
                    """SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                       JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                       WHERE l.OBJECT_NAME='users' AND l.INDEX_NAME='PRIMARY' AND l.LOCK_DATA='990211'""",Int::class.java)!!>0
                if(waiting) break
                if(signingIn.isDone) fail("social login finished before reaching the held member lock")
            }
            assertTrue(waiting,"social login must wait on the member row")
            clock.set(expiry)
        }
        val result=signingIn.get(15,TimeUnit.SECONDS)
        assertEquals(401,result.statusCode(),result.body())
        assertEquals("INVALID_CREDENTIALS",error(result))
        assertPendingWithoutLogin()
    }

    private fun pendingSince(requestedAt:Instant,purgeAt:Instant) {
        jdbc.update("UPDATE users SET status='WITHDRAWAL_PENDING',withdrawal_requested_at=?,purge_scheduled_at=?,last_login_at=NULL WHERE id=990211",
            LocalDateTime.ofInstant(requestedAt,ZoneOffset.UTC),LocalDateTime.ofInstant(purgeAt,ZoneOffset.UTC))
    }
    private fun assertPendingWithoutLogin() {
        val row=jdbc.queryForMap("SELECT status,last_login_at,withdrawal_requested_at,purge_scheduled_at FROM users WHERE id=990211")
        assertEquals("WITHDRAWAL_PENDING",row["status"])
        assertNull(row["last_login_at"])
        assertNotNull(row["withdrawal_requested_at"])
        assertNotNull(row["purge_scheduled_at"])
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=990211",Int::class.java))
    }

    @Test fun `verified merge requires consent consumes ticket once and unlink cannot revive ticket`() {
        val flow=started()
        val available=complete("verified",flow)
        val ticket=ticket(available)
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=990211",Int::class.java))
        val merged=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket"}""",flow.second)
        assertEquals(200,merged.statusCode(),merged.body())
        assertEquals("990211",json.readTree(merged.body()).path("result").path("userId").asString())
        assertEquals(204,send("/api/users/me/social-accounts/google",cookie=accessCookie(),method="DELETE").statusCode())
        val replay=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket"}""",flow.second)
        assertEquals(410,replay.statusCode(),replay.body())
        assertEquals("MERGE_TICKET_EXPIRED",error(replay))
    }

    @Test fun `unverified email cannot merge by consent alone but correct password works`() {
        val flow=started(); val ticket=ticket(complete("unverified",flow))
        val absent=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket"}""",flow.second)
        assertEquals(422,absent.statusCode(),absent.body())
        val wrong=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket","password":"wrong"}""",flow.second)
        assertEquals(401,wrong.statusCode(),wrong.body())
        val correct=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket","password":"ExamplePassword!234"}""",flow.second)
        assertEquals(200,correct.statusCode(),correct.body())
    }

    @Test fun `member linking uses member bound state rejects ownership conflicts and appears in my info`() {
        val loginFlow=started()
        val wrongPurpose=complete("verified",loginFlow,true)
        assertEquals("OAUTH_VERIFICATION_FAILED",error(wrongPurpose))
        val flow=started(true)
        val linked=complete("verified",flow,true)
        assertEquals(201,linked.statusCode(),linked.body())
        val info=send("/api/users/me",cookie=accessCookie())
        assertTrue(json.readTree(info.body()).path("result").path("linkedProviders").any{it.path("provider").asString()=="GOOGLE"},info.body())
        val duplicate=complete("verified",started(true),true)
        assertEquals("PROVIDER_ALREADY_LINKED",error(duplicate))
        assertEquals(204,send("/api/users/me/social-accounts/google",cookie=accessCookie(),method="DELETE").statusCode())
        assertEquals(404,send("/api/users/me/social-accounts/google",cookie=accessCookie(),method="DELETE").statusCode())
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990212,'GOOGLE','new')")
        val inUse=complete("verified",started(true),true)
        assertEquals("SOCIAL_ACCOUNT_IN_USE",error(inUse))
    }

    @Test fun `independent password lock still allows one time account merge email proof`() {
        val flow=started(); val ticket=ticket(complete("unverified",flow))
        repeat(5){index->
            val result=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket","password":"wrong"}""",flow.second)
            assertEquals(if(index==4)429 else 401,result.statusCode(),result.body())
        }
        val locked=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket","password":"ExamplePassword!234"}""",flow.second)
        assertEquals(429,locked.statusCode(),locked.body())
        val token=secrets.generateToken()
        jdbc.update("INSERT INTO email_verifications(email,purpose,code_hash,expires_at,verified_at,token_hash,token_expires_at) VALUES ('social211@example.test','SIGN_UP','hash',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?)",secrets.hashToken(token),LocalDateTime.ofInstant(Instant.now().plusSeconds(1800),ZoneOffset.UTC))
        val wrongPurpose=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket","verificationToken":"$token"}""",flow.second)
        assertEquals(401,wrongPurpose.statusCode(),wrongPurpose.body())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE token_hash=? AND consumed_at IS NOT NULL",Int::class.java,secrets.hashToken(token)))
        jdbc.update("UPDATE email_verifications SET purpose='ACCOUNT_MERGE' WHERE token_hash=?",secrets.hashToken(token))
        val success=send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket","verificationToken":"$token"}""",flow.second)
        assertEquals(200,success.statusCode(),success.body())
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE token_hash=? AND consumed_at IS NOT NULL",Int::class.java,secrets.hashToken(token)))
    }

    @Test fun `concurrent merges create one link and rejected request rolls back ticket consumption`() {
        val first=started(); val second=started()
        val tickets=listOf(ticket(complete("verified",first)) to first.second,ticket(complete("verified",second)) to second.second)
        val barrier=CyclicBarrier(2);val pool=Executors.newFixedThreadPool(2)
        try {
            val responses=tickets.map{(ticket,cookie)->pool.submit(Callable{barrier.await(10,TimeUnit.SECONDS);send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket"}""",cookie)})}.map{it.get(20,TimeUnit.SECONDS)}
            assertEquals(listOf(200,409),responses.map{it.statusCode()}.sorted(),responses.joinToString{it.body()})
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=990211",Int::class.java))
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=990211",Int::class.java))
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs WHERE user_id=990211 AND kind='MERGE' AND consumed_at IS NOT NULL",Int::class.java))
        }finally{pool.shutdownNow()}
    }

    @Test fun `expired ticket and other browser cannot merge and protected start requires login`() {
        val flow=started(); val ticket=ticket(complete("verified",flow))
        assertEquals(410,send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket"}""").statusCode())
        jdbc.update("UPDATE oauth_proofs SET expires_at=? WHERE token_hash=?",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),secrets.hashToken(ticket))
        assertEquals(410,send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket"}""",flow.second).statusCode())
        assertEquals(401,send("/api/users/me/social-accounts/google/start","""{"redirectUri":"https://example.test/callback"}""").statusCode())
        assertEquals(401,send("/api/auth/oauth/google/start","""{"redirectUri":"https://evil.test/callback"}""").statusCode())
    }

    @Test fun `login start binds cookie and completing an unknown social account requires consent before member creation`() {
        HttpClient.newHttpClient().use { client ->
            fun send(path:String,body:String,cookie:String?=null):HttpResponse<String> {
                val request=HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type","application/json")
                if(cookie!=null) request.header("Cookie",cookie)
                return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
            }
            val started=send("/api/auth/oauth/google/start","""{"redirectUri":"https://example.test/callback"}""")
            assertEquals(200,started.statusCode(),started.body())
            val cookie=started.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
            val url=json.readTree(started.body()).path("result").path("authorizationUrl").asString()
            val state=URLDecoder.decode(URI(url).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
            val body="""{"authorizationCode":"unknown","redirectUri":"https://example.test/callback","state":"$state"}"""
            val wrongBrowser=send("/api/auth/oauth/google",body)
            assertEquals(401,wrongBrowser.statusCode())
            val completed=send("/api/auth/oauth/google",body,cookie)
            assertEquals(409,completed.statusCode(),completed.body())
            assertEquals("SOCIAL_SIGN_UP_REQUIRED",json.readTree(completed.body()).path("result").path("errorCode").asString())
            val replay=send("/api/auth/oauth/google",body,cookie)
            assertEquals("OAUTH_VERIFICATION_FAILED",json.readTree(replay.body()).path("result").path("errorCode").asString())
        }
    }

    @Test fun `new social identity receives consent ticket then signs up without email or password`() {
        jdbc.update("INSERT INTO terms(id,code,version,title,content_url,is_required,seq,effective_at) VALUES (990356,'SOCIAL_TEST','v1','필수','https://example.test',TRUE,99,'2020-01-01')")
        try {
            val flow=started()
            val pending=complete("unknown",flow)
            assertEquals(409,pending.statusCode(),pending.body())
            assertEquals("SOCIAL_SIGN_UP_REQUIRED",error(pending))
            val ticket=json.readTree(pending.body()).path("result").path("details").path("signupTicket").asString()
            val body="""{"signupTicket":"$ticket","nickname":"소셜신규","agreements":[{"termId":"990356","version":"v1","agreed":true}]}"""
            assertEquals(410,send("/api/auth/oauth/sign-up",body).statusCode())
            val created=send("/api/auth/oauth/sign-up",body,flow.second)
            assertEquals(201,created.statusCode(),created.body())
            val id=json.readTree(created.body()).path("result").path("userId").asString().toLong()
            val row=jdbc.queryForMap("SELECT email,password_hash FROM users WHERE id=?",id)
            assertNull(row["email"]);assertNull(row["password_hash"])
            assertEquals(2,created.headers().allValues("Set-Cookie").size)
            val auth=created.headers().allValues("Set-Cookie").first{it.startsWith("access_token=")}.substringBefore(';')
            val me=send("/api/users/me",cookie=auth)
            assertEquals(200,me.statusCode(),me.body())
            assertTrue(json.readTree(me.body()).path("result").path("email").isNull,me.body())
            assertEquals(410,send("/api/auth/oauth/sign-up",body,flow.second).statusCode())
            val signed=complete("unknown")
            assertEquals(200,signed.statusCode(),signed.body())
            val cookie=created.headers().allValues("Set-Cookie").first{it.startsWith("access_token=")}.substringBefore(';')
            val unlink=HttpClient.newHttpClient().use{it.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me/social-accounts/google")).header("Cookie",cookie).DELETE().build(),HttpResponse.BodyHandlers.ofString())}
            assertEquals(409,unlink.statusCode(),unlink.body())
            assertEquals("LAST_LOGIN_METHOD",error(unlink))
            val withdrawalStart=send("/api/users/me/withdrawal/oauth/google/start","""{"redirectUri":"https://example.test/callback"}""",cookie)
            assertEquals(200,withdrawalStart.statusCode(),withdrawalStart.body())
            val withdrawalBinding=withdrawalStart.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
            val withdrawalUrl=json.readTree(withdrawalStart.body()).path("result").path("authorizationUrl").asString()
            val withdrawalState=URLDecoder.decode(URI(withdrawalUrl).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
            val withdrawalCode="""{"authorizationCode":"unknown","redirectUri":"https://example.test/callback","state":"$withdrawalState"}"""
            val confirmation=send("/api/users/me/withdrawal/oauth/google",withdrawalCode,"$cookie; $withdrawalBinding")
            assertEquals(200,confirmation.statusCode(),confirmation.body())
            val withdrawalTicket=json.readTree(confirmation.body()).path("result").path("withdrawalTicket").asString()
            val withdrawn=send("/api/users/me","""{"withdrawalTicket":"$withdrawalTicket"}""","$cookie; $withdrawalBinding","DELETE")
            assertEquals(200,withdrawn.statusCode(),withdrawn.body())
            assertEquals("WITHDRAWAL_PENDING",jdbc.queryForObject("SELECT status FROM users WHERE id=?",String::class.java,id))
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",id)
            jdbc.update("DELETE FROM user_term_agreements WHERE user_id=?",id)
            jdbc.update("DELETE FROM user_social_accounts WHERE user_id=?",id)
            jdbc.update("DELETE FROM users WHERE id=?",id)
        } finally { jdbc.update("DELETE FROM user_term_agreements WHERE term_id=990356");jdbc.update("DELETE FROM terms WHERE id=990356") }
    }

    private fun signupFlow():Pair<String,String> {
        val flow=started()
        val result=complete("unknown",flow)
        assertEquals(409,result.statusCode(),result.body())
        return json.readTree(result.body()).path("result").path("details").path("signupTicket").asString() to flow.second
    }
    private fun signupBody(ticket:String,agreed:Boolean=true)="""{"signupTicket":"$ticket","nickname":"소셜신규","agreements":[{"termId":"990356","version":"v1","agreed":$agreed}]}"""
    private fun signupTerms(){jdbc.update("INSERT INTO terms(id,code,version,title,content_url,is_required,seq,effective_at) VALUES (990356,'SOCIAL_TEST','v1','필수','https://example.test',TRUE,99,'2020-01-01')")}

    @Test fun `invalid consent expiry purpose and browser cannot consume signup proof`() {
        signupTerms()
        val flow=signupFlow()
        assertEquals(422,send("/api/auth/oauth/sign-up",signupBody(flow.first,false),flow.second).statusCode())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs WHERE token_hash=? AND consumed_at IS NOT NULL",Int::class.java,secrets.hashToken(flow.first)))
        assertEquals(410,send("/api/auth/oauth/merge","""{"mergeTicket":"${flow.first}"}""",flow.second).statusCode())
        jdbc.update("UPDATE oauth_proofs SET expires_at=? WHERE token_hash=?",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),secrets.hashToken(flow.first))
        assertEquals(410,send("/api/auth/oauth/sign-up",signupBody(flow.first),flow.second).statusCode())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE nickname='소셜신규'",Int::class.java))
    }

    @Test fun `distinct signup tickets racing for same identity create exactly one member`() {
        signupTerms()
        val flows=listOf(signupFlow(),signupFlow())
        val barrier=CyclicBarrier(2);val pool=Executors.newFixedThreadPool(2)
        try {
            val responses=flows.map{flow->pool.submit(Callable{barrier.await(10,TimeUnit.SECONDS);send("/api/auth/oauth/sign-up",signupBody(flow.first),flow.second)})}.map{it.get(20,TimeUnit.SECONDS)}
            assertEquals(listOf(201,409),responses.map{it.statusCode()}.sorted(),responses.joinToString{it.body()})
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE nickname='소셜신규'",Int::class.java))
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id IN(SELECT id FROM users WHERE nickname='소셜신규')",Int::class.java))
        }finally{pool.shutdownNow()}
    }

    @Test fun `token persistence failure rolls back member agreement link and ticket together`() {
        signupTerms()
        val flow=signupFlow()
        jdbc.execute("CREATE TRIGGER oauth356_fail_refresh BEFORE INSERT ON refresh_tokens FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test token persistence failure'")
        try {
            assertEquals(500,send("/api/auth/oauth/sign-up",signupBody(flow.first),flow.second).statusCode())
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE nickname='소셜신규'",Int::class.java))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_social_accounts WHERE provider_user_id='unknown-subject'",Int::class.java))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs WHERE token_hash=? AND consumed_at IS NOT NULL",Int::class.java,secrets.hashToken(flow.first)))
        }finally{jdbc.execute("DROP TRIGGER oauth356_fail_refresh")}
        assertEquals(201,send("/api/auth/oauth/sign-up",signupBody(flow.first),flow.second).statusCode())
    }

    @Test fun `all providers sign up and only verified email is stored without creating password login`() {
        signupTerms()
        for(provider in listOf("kakao","google","naver")) {
            val start=send("/api/auth/oauth/$provider/start","""{"redirectUri":"https://example.test/callback"}""")
            assertEquals(200,start.statusCode(),start.body())
            val cookie=start.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
            val url=json.readTree(start.body()).path("result").path("authorizationUrl").asString()
            val state=URLDecoder.decode(URI(url).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
            val pending=send("/api/auth/oauth/$provider","""{"authorizationCode":"fresh356","redirectUri":"https://example.test/callback","state":"$state"}""",cookie)
            assertEquals(409,pending.statusCode(),pending.body())
            val ticket=json.readTree(pending.body()).path("result").path("details").path("signupTicket").asString()
            val signed=send("/api/auth/oauth/sign-up",signupBody(ticket),cookie)
            assertEquals(201,signed.statusCode(),signed.body())
            val id=json.readTree(signed.body()).path("result").path("userId").asString().toLong()
            val email="fresh356-$provider@example.test"
            val row=jdbc.queryForMap("SELECT email,password_hash FROM users WHERE id=?",id)
            assertEquals(if(provider=="naver")null else email,row["email"])
            assertNull(row["password_hash"])
            assertEquals(401,send("/api/auth/sign-in","""{"email":"$email","password":"ExamplePassword!234"}""").statusCode())
            if(provider!="naver") {
                val token=secrets.generateToken()
                jdbc.update("INSERT INTO email_verifications(email,purpose,code_hash,expires_at,verified_at,token_hash,token_expires_at) VALUES (?,'PASSWORD_RESET','hash',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?)",email,secrets.hashToken(token),LocalDateTime.ofInstant(clock.instant().plusSeconds(1800),ZoneOffset.UTC))
                assertEquals(401,send("/api/auth/password-reset","""{"verificationToken":"$token","newPassword":"ExamplePassword!345"}""").statusCode())
                assertNull(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String::class.java,id))
            }
        }
    }

    private fun withdrawalProof():Pair<String,String> {
        val started=send("/api/users/me/withdrawal/oauth/google/start","""{"redirectUri":"https://example.test/callback"}""",accessCookie())
        assertEquals(200,started.statusCode(),started.body())
        val binding=started.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
        val url=json.readTree(started.body()).path("result").path("authorizationUrl").asString()
        val state=URLDecoder.decode(URI(url).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
        val body="""{"authorizationCode":"linked","redirectUri":"https://example.test/callback","state":"$state"}"""
        val confirmed=send("/api/users/me/withdrawal/oauth/google",body,"${accessCookie()}; $binding")
        assertEquals(200,confirmed.statusCode(),confirmed.body())
        return json.readTree(confirmed.body()).path("result").path("withdrawalTicket").asString() to binding
    }

    @Test fun `reconnecting the same social identity rejects a previous withdrawal ticket`() {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990211,'GOOGLE','existing')")
        val proof=withdrawalProof()
        assertEquals(204,send("/api/users/me/social-accounts/google",cookie=accessCookie(),method="DELETE").statusCode())
        val relinked=complete("linked",started(true),true)
        assertEquals(201,relinked.statusCode(),relinked.body())
        val rejected=send("/api/users/me","""{"withdrawalTicket":"${proof.first}"}""","${accessCookie()}; ${proof.second}","DELETE")
        assertEquals(410,rejected.statusCode(),rejected.body())
        assertEquals("WITHDRAWAL_TICKET_EXPIRED",error(rejected))
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=990211",String::class.java))
        val fresh=withdrawalProof()
        assertEquals(200,send("/api/users/me","""{"withdrawalTicket":"${fresh.first}"}""","${accessCookie()}; ${fresh.second}","DELETE").statusCode())
    }

    @Test fun `withdrawal authentication started before reconnect cannot issue a ticket for new link`() {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990211,'GOOGLE','existing')")
        val start=send("/api/users/me/withdrawal/oauth/google/start","""{"redirectUri":"https://example.test/callback"}""",accessCookie())
        assertEquals(200,start.statusCode(),start.body())
        val binding=start.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
        val url=json.readTree(start.body()).path("result").path("authorizationUrl").asString()
        val state=URLDecoder.decode(URI(url).rawQuery.substringAfter("state=").substringBefore('&'),Charsets.UTF_8)
        assertEquals(204,send("/api/users/me/social-accounts/google",cookie=accessCookie(),method="DELETE").statusCode())
        assertEquals(201,complete("linked",started(true),true).statusCode())
        val rejected=send("/api/users/me/withdrawal/oauth/google","""{"authorizationCode":"linked","redirectUri":"https://example.test/callback","state":"$state"}""","${accessCookie()}; $binding")
        assertEquals(401,rejected.statusCode(),rejected.body())
        assertEquals("OAUTH_VERIFICATION_FAILED",error(rejected))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs WHERE user_id=990211 AND kind='WITHDRAW'",Int::class.java))
    }

    @Test fun `withdrawal proof cannot cross members browsers purposes or expiry`() {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990211,'GOOGLE','existing')")
        val proof=withdrawalProof()
        val body="""{"withdrawalTicket":"${proof.first}"}"""
        assertEquals(410,send("/api/users/me",body,"${accessCookie(990212)}; ${proof.second}","DELETE").statusCode())
        assertEquals(410,send("/api/users/me",body,accessCookie(),"DELETE").statusCode())
        assertEquals(410,send("/api/auth/oauth/sign-up",signupBody(proof.first),proof.second).statusCode())
        assertEquals(400,send("/api/users/me","""{"withdrawalTicket":"${proof.first}","currentPassword":"ExamplePassword!234"}""","${accessCookie()}; ${proof.second}","DELETE").statusCode())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs WHERE token_hash=? AND consumed_at IS NOT NULL",Int::class.java,secrets.hashToken(proof.first)))
        jdbc.update("UPDATE oauth_proofs SET expires_at=? WHERE token_hash=?",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),secrets.hashToken(proof.first))
        assertEquals(410,send("/api/users/me",body,"${accessCookie()}; ${proof.second}","DELETE").statusCode())
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=990211",String::class.java))
    }

    @Test fun `withdrawal failure rolls back proof and member status and retry succeeds`() {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id) VALUES (990211,'GOOGLE','existing')")
        val proof=withdrawalProof()
        val body="""{"withdrawalTicket":"${proof.first}"}"""
        val cookie="${accessCookie()}; ${proof.second}"
        jdbc.execute("CREATE TRIGGER oauth356_fail_refresh BEFORE UPDATE ON users FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test withdrawal failure'")
        try {
            assertEquals(500,send("/api/users/me",body,cookie,"DELETE").statusCode())
            assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=990211",String::class.java))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs WHERE token_hash=? AND consumed_at IS NOT NULL",Int::class.java,secrets.hashToken(proof.first)))
        }finally{jdbc.execute("DROP TRIGGER oauth356_fail_refresh")}
        assertEquals(200,send("/api/users/me",body,cookie,"DELETE").statusCode())
        assertEquals(401,send("/api/users/me",body,cookie,"DELETE").statusCode())
    }

    @TestConfiguration(proxyBeanMethods=false)
    class Fixtures {
        @Bean @Primary fun socialTestClock()=VerificationTestClock()
        @Bean @Primary fun fakeOAuthGateway()=object:OAuthGateway {
            override fun ready(provider:SocialProvider)=true
            override fun allows(provider:SocialProvider,redirectUri:String)=redirectUri=="https://example.test/callback"
            override fun authorizationUrl(provider:SocialProvider,redirectUri:String,state:String,verifier:String)="https://provider.example.test/authorize?state=$state"
            override fun identify(provider:SocialProvider,redirectUri:String,code:String,state:String,verifier:String)=when(code){
                "fresh356"->OAuthIdentity("fresh356-${provider.name}","fresh356-${provider.name.lowercase()}@example.test",provider!=SocialProvider.NAVER)
                "linked"->OAuthIdentity("existing",null,false)
                "verified"->OAuthIdentity("new","social211@example.test",true)
                "apple"->OAuthIdentity("apple-subject","relay@privaterelay.appleid.com",false,providerToken="apple-refresh")
                "apple-merge"->OAuthIdentity("apple-subject","social211@example.test",false,providerToken="apple-merge-refresh")
                "unverified"->OAuthIdentity("new","social211@example.test",false)
                else->OAuthIdentity("unknown-subject",null,false)
            }
            override fun revoke(provider:SocialProvider,token:String){revoked+=token}
        }
    }
    companion object {
        val revoked=java.util.concurrent.CopyOnWriteArrayList<String>()
        private val applePem="-----BEGIN PRIVATE KEY-----\n"+java.util.Base64.getEncoder().encodeToString(
            java.security.KeyPairGenerator.getInstance("EC").apply{initialize(java.security.spec.ECGenParameterSpec("secp256r1"))}.generateKeyPair().private.encoded)+"\n-----END PRIVATE KEY-----"
        @JvmStatic @DynamicPropertySource fun properties(registry:DynamicPropertyRegistry) {
            registry.add("eolssu.auth.oauth.apple.private-key"){applePem}
            registry.add("eolssu.scheduling.enabled"){"false"}
            registry.add("eolssu.security.jwt-secret"){"test-signing-key-for-social-auth-at-least-32-bytes"}
            registry.add("spring.datasource.url"){requireNotNull(System.getenv("TEST_DB_URL"))}
            registry.add("spring.datasource.username"){requireNotNull(System.getenv("TEST_DB_USERNAME"))}
            registry.add("spring.datasource.password"){requireNotNull(System.getenv("TEST_DB_PASSWORD"))}
        }
    }
}

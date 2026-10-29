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
import java.util.concurrent.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*
import org.springframework.test.context.*
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
    @BeforeEach fun seed() {
        cleanup()
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,status) VALUES (990211,'social211@example.test',?,'소셜',1,'ACTIVE')",passwords.encode("ExamplePassword!234"))
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,status) VALUES (990212,'other211@example.test',?,'다른회원',1,'ACTIVE')",passwords.encode("OtherPassword!234"))
    }
    @AfterEach fun cleanup() {
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
        jdbc.update("UPDATE oauth_proofs SET expires_at=UTC_TIMESTAMP(6) WHERE token_hash=?",secrets.hashToken(ticket))
        assertEquals(410,send("/api/auth/oauth/merge","""{"mergeTicket":"$ticket"}""",flow.second).statusCode())
        assertEquals(401,send("/api/users/me/social-accounts/google/start","""{"redirectUri":"https://example.test/callback"}""").statusCode())
        assertEquals(401,send("/api/auth/oauth/google/start","""{"redirectUri":"https://evil.test/callback"}""").statusCode())
    }

    @Test fun `login start binds cookie and completing an unknown social account never creates a member`() {
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
            assertEquals(401,completed.statusCode(),completed.body())
            assertEquals("SOCIAL_ACCOUNT_NOT_REGISTERED",json.readTree(completed.body()).path("result").path("errorCode").asString())
            val replay=send("/api/auth/oauth/google",body,cookie)
            assertEquals("OAUTH_VERIFICATION_FAILED",json.readTree(replay.body()).path("result").path("errorCode").asString())
        }
    }

    @TestConfiguration(proxyBeanMethods=false)
    class Fixtures {
        @Bean @Primary fun fakeOAuthGateway()=object:OAuthGateway {
            override fun ready(provider:SocialProvider)=true
            override fun allows(provider:SocialProvider,redirectUri:String)=redirectUri=="https://example.test/callback"
            override fun authorizationUrl(provider:SocialProvider,redirectUri:String,state:String,verifier:String)="https://provider.example.test/authorize?state=$state"
            override fun identify(provider:SocialProvider,redirectUri:String,code:String,state:String,verifier:String)=when(code){
                "linked"->OAuthIdentity("existing",null,false)
                "verified"->OAuthIdentity("new","social211@example.test",true)
                "unverified"->OAuthIdentity("new","social211@example.test",false)
                else->OAuthIdentity("unknown-subject",null,false)
            }
        }
    }
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry:DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled"){"false"}
            registry.add("eolssu.security.jwt-secret"){"test-signing-key-for-social-auth-at-least-32-bytes"}
            registry.add("spring.datasource.url"){requireNotNull(System.getenv("TEST_DB_URL"))}
            registry.add("spring.datasource.username"){requireNotNull(System.getenv("TEST_DB_USERNAME"))}
            registry.add("spring.datasource.password"){requireNotNull(System.getenv("TEST_DB_PASSWORD"))}
        }
    }
}

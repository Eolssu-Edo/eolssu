package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.config.PasswordConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmailVerificationApiDatabaseTests.Fixtures::class)
class PasswordResetApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var clock: VerificationTestClock
    @Value("\${local.server.port}") private var port = 0
    @Autowired private lateinit var loginAttempts: com.ssafy.eolssu.backend.feature.auth.application.LoginAttemptLimiter
    @Autowired private lateinit var users: com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val encoder = PasswordConfig().passwordEncoder()
    private var userId = 0L

    @BeforeEach fun before() {
        cleanup()
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        loginAttempts.reset(users.emailSortKey("reset@auth210.test"))
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990610,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990610,'RESET210','검증',990610,990610,50)")
        jdbc.update("INSERT INTO users (email,password_hash,nickname,instrument_id,status) VALUES ('reset@auth210.test',?,'검증',990610,'ACTIVE')",encoder.encode("Current!234"))
        userId=jdbc.queryForObject("SELECT id FROM users WHERE instrument_id=990610",Long::class.java)!!
    }
    @AfterEach fun after() { client.close(); cleanup() }
    private fun cleanup() {
        jdbc.update("DELETE FROM email_verifications WHERE email LIKE '%@auth210.test'")
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE instrument_id=990610)")
        jdbc.update("DELETE FROM users WHERE instrument_id=990610")
        jdbc.update("DELETE FROM instruments WHERE id=990610")
        jdbc.update("DELETE FROM pitches WHERE id=990610")
    }
    private fun proof(token: String="proof", purpose: String="PASSWORD_RESET", email: String="reset@auth210.test", expiry: Instant=clock.instant().plusSeconds(1800)) {
        val hash=com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets(null).hashToken(token)
        jdbc.update("INSERT INTO email_verifications(email,purpose,code_hash,expires_at,verified_at,token_hash,token_expires_at) VALUES (?,?,'hash',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?)",email,purpose,hash,java.time.LocalDateTime.ofInstant(expiry,java.time.ZoneOffset.UTC))
    }
    private fun post(path: String, body: String, cookie: String?=null): HttpResponse<String> {
        val builder=HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type","application/json").timeout(Duration.ofSeconds(15))
        cookie?.let { builder.header("Cookie",it) }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
    }
    private fun reset(token: String="proof", password: String="NewPassword!567")=post("/api/auth/password-reset", """{"verificationToken":"$token","newPassword":"$password"}""")
    private fun error(response: HttpResponse<String>,status: Int,code: String) {
        assertEquals(status,response.statusCode(),response.body())
        assertEquals(code,json.readTree(response.body()).path("result").path("errorCode").asString())
    }
    @Test fun `reset consumes proof updates hash and revokes every refresh without login`() {
        val signIn=post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"Current!234"}""")
        assertEquals(200,signIn.statusCode(),signIn.body())
        val secondDevice=post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"Current!234"}""")
        assertEquals(200,secondDevice.statusCode(),secondDevice.body())
        proof()
        val response=reset()
        assertEquals(204,response.statusCode(),response.body())
        assertEquals("",response.body())
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty())
        assertTrue(encoder.matches("NewPassword!567",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String::class.java,userId)))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL",Int::class.java,userId))
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=? AND revoke_reason='PASSWORD_CHANGED'",Int::class.java,userId))
        for (device in listOf(signIn,secondDevice)) {
            val refresh=device.headers().allValues("Set-Cookie").first { it.startsWith("refresh_token=") }.substringBefore(';')
            error(post("/api/auth/refresh","{}",refresh),401,"UNAUTHENTICATED")
        }
        error(reset(),401,"EMAIL_NOT_VERIFIED")
        assertEquals(401,post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"Current!234"}""").statusCode())
        assertEquals(200,post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"NewPassword!567"}""").statusCode())
    }
    @Test fun `invalid proofs cannot change password or consume proof`() {
        proof("expired",expiry=clock.instant())
        proof("wrong-purpose",purpose="SIGN_UP")
        proof("invalidated")
        proof("consumed")
        proof("alias",email="rését@auth210.test")
        proof("absent",email="absent@auth210.test")
        jdbc.update("UPDATE email_verifications SET invalidated_at=UTC_TIMESTAMP(6) WHERE token_hash=?",hash("invalidated"))
        jdbc.update("UPDATE email_verifications SET consumed_at=UTC_TIMESTAMP(6) WHERE token_hash=?",hash("consumed"))
        for (token in listOf("expired","wrong-purpose","invalidated","consumed","alias","absent","unknown")) {
            val response=reset(token)
            assertEquals(401,response.statusCode(),"proof case=$token: ${response.body()}")
            error(response,401,"EMAIL_NOT_VERIFIED")
        }
        assertTrue(encoder.matches("Current!234",storedHash()))
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE consumed_at IS NOT NULL AND email LIKE '%@auth210.test'",Int::class.java))
    }
    @Test fun `same and invalid passwords leave proof available for retry and malformed input is 400`() {
        proof()
        for (password in listOf("Current!234","nodigits!","Short1!","a1!"+"x".repeat(126))) error(reset(password=password),400,"INVALID_REQUEST")
        for (body in listOf("{}","{","{\"verificationToken\":\"proof\"}","{\"newPassword\":\"NewPassword!567\"}")) error(post("/api/auth/password-reset",body),400,"INVALID_REQUEST")
        assertEquals(204,reset().statusCode())
    }
    @Test fun `withdrawal pending reset does not cancel withdrawal`() {
        jdbc.update("UPDATE users SET status='WITHDRAWAL_PENDING',withdrawal_requested_at='2026-10-28',purge_scheduled_at='2026-11-27' WHERE id=?",userId)
        proof()
        assertEquals(204,reset().statusCode())
        assertEquals("WITHDRAWAL_PENDING",jdbc.queryForObject("SELECT status FROM users WHERE id=?",String::class.java,userId))
        assertEquals("2026-11-27",jdbc.queryForObject("SELECT CAST(DATE(purge_scheduled_at) AS CHAR) FROM users WHERE id=?",String::class.java,userId))
    }
    @Test fun `simultaneous consumption succeeds exactly once`() {
        proof()
        val start=java.util.concurrent.CountDownLatch(1)
        val tasks=(1..2).map { java.util.concurrent.CompletableFuture.supplyAsync { start.await(); reset(password="Concurrent!$it").statusCode() } }
        start.countDown()
        assertEquals(listOf(204,401),tasks.map { it.get(20,java.util.concurrent.TimeUnit.SECONDS) }.sorted())
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email='reset@auth210.test' AND consumed_at IS NOT NULL",Int::class.java))
    }
    @Test fun `reset is independent from login and current password counters and preserves them`() {
        val signed=post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"Current!234"}""")
        val access=signed.headers().allValues("Set-Cookie").first { it.startsWith("access_token=") }.substringBefore(';')
        fun wrongCurrent(): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me/password"))
            .header("Content-Type","application/json").header("Cookie",access).method("PATCH",HttpRequest.BodyPublishers.ofString("""{"currentPassword":"Wrong!234","newPassword":"Another!234"}""")).build(),HttpResponse.BodyHandlers.ofString())
        repeat(4) { error(wrongCurrent(),403,"CURRENT_PASSWORD_MISMATCH") }
        repeat(5) { post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"Wrong!234"}""") }
        proof()
        assertEquals(204,reset().statusCode())
        error(wrongCurrent(),429,"TOO_MANY_REQUESTS")
        error(post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"NewPassword!567"}"""),429,"TOO_MANY_REQUESTS")
    }
    @Test fun `failure during refresh revocation rolls back password and proof consumption`() {
        jdbc.execute("CREATE TRIGGER reset210_revoke_failure BEFORE UPDATE ON refresh_tokens FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='reset rollback probe'")
        try {
            post("/api/auth/sign-in","""{"email":"reset@auth210.test","password":"Current!234"}""")
            proof()
            assertEquals(500,reset().statusCode())
            assertTrue(encoder.matches("Current!234",storedHash()))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email='reset@auth210.test' AND consumed_at IS NOT NULL",Int::class.java))
        } finally { jdbc.execute("DROP TRIGGER IF EXISTS reset210_revoke_failure") }
        assertEquals(204,reset().statusCode())
    }
    @Test fun `email send confirm and reset work together with a stale access cookie`() {
        val mail=mailFixture
        mail.messages.clear()
        val sent=post("/api/auth/email-verifications","""{"email":"reset@auth210.test","purpose":"PASSWORD_RESET"}""")
        assertEquals(202,sent.statusCode(),sent.body())
        val id=json.readTree(sent.body()).path("result").path("verificationId").asString()
        val verified=post("/api/auth/email-verifications/$id/confirm","""{"code":"${mail.messages.single()}"}""")
        assertEquals(200,verified.statusCode(),verified.body())
        val token=json.readTree(verified.body()).path("result").path("verificationToken").asString()
        val response=post("/api/auth/password-reset","""{"verificationToken":"$token","newPassword":"NewPassword!567"}""","access_token=invalid")
        assertEquals(204,response.statusCode(),response.body())
    }
    @Autowired private lateinit var mailFixture: CapturingVerificationMail
    private fun hash(token: String)=com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets(null).hashToken(token)
    private fun storedHash()=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String::class.java,userId)

    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("eolssu.security.jwt-secret") { "test-signing-key-at-least-32-bytes-long!!" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String) = requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}

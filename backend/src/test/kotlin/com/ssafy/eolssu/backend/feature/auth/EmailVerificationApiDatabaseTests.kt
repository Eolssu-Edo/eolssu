package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.zaxxer.hikari.HikariDataSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.*
import java.util.HexFormat
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
class EmailVerificationApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var clock: VerificationTestClock
    @Autowired private lateinit var mail: CapturingVerificationMail
    @Value("\${local.server.port}") private var port = 0
    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @BeforeEach
    fun before() { cleanup(); clock.set(Instant.parse("2026-10-28T03:00:00Z")); mail.messages.clear(); mail.recipients.clear(); mail.fail = false }

    @AfterEach
    fun after() { client.close(); cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM email_verifications WHERE email LIKE '%@auth143.test'")
        jdbc.update("DELETE FROM users WHERE id IN (990001, 990002)")
        jdbc.update("DELETE FROM instruments WHERE id = 990001")
        jdbc.update("DELETE FROM pitches WHERE id = 990001")
    }

    @Test
    fun `anonymous HTTP send and confirm persist hashes and issue only one token`() {
        val sent = send("Learner@auth143.test")
        assertEquals(202, sent.statusCode(), sent.body())
        val body = tree(sent)
        assertEquals("ACCEPTED", body.path("httpStatus").asString())
        assertTrue(body.path("isSuccess").asBoolean())
        val id = body.path("result").path("verificationId").asString().toLong()
        assertEquals("2026-10-28T03:03:00Z", body.path("result").path("expiresAt").asString())
        assertEquals("2026-10-28T03:01:00Z", body.path("result").path("resendAvailableAt").asString())
        val code = mail.messages.single()
        assertFalse(sent.body().contains(code))
        assertEquals("learner@auth143.test", jdbc.queryForObject("SELECT email FROM email_verifications WHERE id = ?", String::class.java, id))
        val hash = jdbc.queryForObject("SELECT code_hash FROM email_verifications WHERE id = ?", String::class.java, id)!!
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(code, hash)
        val confirmed = confirm(id, code)
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        val token = tree(confirmed).path("result").path("verificationToken").asString()
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals("2026-10-28T03:30:00Z", tree(confirmed).path("result").path("expiresAt").asString())
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray())), jdbc.queryForObject("SELECT token_hash FROM email_verifications WHERE id = ?", String::class.java, id))
        assertError(confirm(id, code), 410, "VERIFICATION_EXPIRED")
    }

    @Test
    fun `reset collation alias delivers only to the registered email and binds the proof to it`() {
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990001,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990001,'AUTH_TEST','검증',990001,990001,50)")
        jdbc.update("INSERT INTO users (id,email,password_hash,nickname,instrument_id,status) VALUES (990001,'Member@auth143.test','test-hash','검증',990001,'ACTIVE')")
        // 형식 규칙(S15P31E102-686)은 ASCII 주소만 받으므로 악센트 변형은 400이고, 대소문자 변형으로 원래 주소 발송을 확인한다.
        assertError(send("mémber@auth143.test", "PASSWORD_RESET"), 400, "INVALID_REQUEST")
        val sent = send("MEMBER@auth143.test", "PASSWORD_RESET")
        assertEquals(202, sent.statusCode(), sent.body())
        assertEquals(listOf("Member@auth143.test"), mail.recipients.toList())
        val id = tree(sent).path("result").path("verificationId").asString().toLong()
        assertEquals("Member@auth143.test", jdbc.queryForObject("SELECT email FROM email_verifications WHERE id = ?", String::class.java, id))
        val verified = confirm(id, mail.messages.single())
        assertEquals(200, verified.statusCode(), verified.body())
        assertEquals("Member@auth143.test", tree(verified).path("result").path("email").asString())
    }

    @Test
    fun `invalid public input is common 400 without writing or sending`() {
        for (body in listOf("{}", "{\"email\":\"bad\",\"purpose\":\"SIGN_UP\"}", "{\"email\":\"a@b\",\"purpose\":\"PASSWORD_RESET\"}", "{\"email\":\"a..b@auth143.test\",\"purpose\":\"SIGN_UP\"}", "{\"email\":\"a@auth143.test\",\"purpose\":\"UNKNOWN\"}", "{")) {
            assertError(post("/api/auth/email-verifications", body), 400, "INVALID_REQUEST")
        }
        for (path in listOf("not-a-number", "-1", "0")) assertError(post("/api/auth/email-verifications/$path/confirm", "{\"code\":\"123456\"}"), 400, "INVALID_REQUEST")
        assertError(post("/api/auth/email-verifications/1/confirm", "{\"code\":\"12345x\"}"), 400, "INVALID_REQUEST")
        assertError(post("/api/auth/email-verifications/1/confirm", "{\"code\":123456}"), 400, "INVALID_REQUEST")
        assertEquals(0, count())
        assertTrue(mail.messages.isEmpty())
    }

    @Test
    fun `email normalization exceeding the storage length returns 400 without sending`() {
        // 요청 길이는 100자 안이지만 소문자로 바꾸면 İ가 두 글자가 되어 이메일 상한(100자)을 넘는다(S15P31E102-1014).
        val email = "İ".repeat(40) + "@" + "a".repeat(55) + ".com"
        assertTrue(email.length <= 100)
        assertError(send(email), 400, "INVALID_REQUEST")
        assertEquals(0, count())
        assertTrue(mail.messages.isEmpty())
    }

    @Test
    fun `resend rate limit and delivery failure retain the previous usable code`() {
        val first = send("resend@auth143.test")
        assertEquals(202, first.statusCode())
        val id = tree(first).path("result").path("verificationId").asString().toLong()
        val code = mail.messages.single()
        assertError(send("RESEND@auth143.test", "PASSWORD_RESET"), 429, "TOO_MANY_REQUESTS")
        clock.set(clock.instant().plusSeconds(60))
        mail.fail = true
        assertError(send("resend@auth143.test"), 503, "SERVICE_UNAVAILABLE")
        assertEquals(1, count())
        assertNull(jdbc.queryForObject("SELECT invalidated_at FROM email_verifications WHERE id = ?", LocalDateTime::class.java, id))
        assertEquals(200, confirm(id, code).statusCode())
        mail.fail = false
        assertEquals(202, send("resend@auth143.test").statusCode())
        assertNotNull(jdbc.queryForObject("SELECT invalidated_at FROM email_verifications WHERE id = ?", LocalDateTime::class.java, id))
    }

    @Test
    fun `five wrong attempts commit despite the error responses`() {
        val sent = send("wrong@auth143.test")
        assertEquals(202, sent.statusCode())
        val id = tree(sent).path("result").path("verificationId").asString().toLong()
        val code = mail.messages.single()
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(4) { index ->
            val response = confirm(id, wrong)
            assertError(response, 400, "VERIFICATION_CODE_MISMATCH")
            assertEquals(4 - index, tree(response).path("result").path("details").path("remainingAttempts").asInt())
        }
        assertError(confirm(id, wrong), 429, "TOO_MANY_REQUESTS")
        assertEquals(5, jdbc.queryForObject("SELECT attempt_count FROM email_verifications WHERE id = ?", Int::class.java, id))
        assertError(confirm(id, code), 429, "TOO_MANY_REQUESTS")
    }

    @Test
    fun `expiry missing IDs and reset privacy have documented HTTP responses`() {
        assertError(confirm(999999, "123456"), 404, "RESOURCE_NOT_FOUND")
        val sent = send("expired@auth143.test")
        assertEquals(202, sent.statusCode())
        val id = tree(sent).path("result").path("verificationId").asString().toLong()
        clock.set(clock.instant().plusSeconds(180))
        assertError(confirm(id, mail.messages.single()), 410, "VERIFICATION_EXPIRED")
        mail.messages.clear()
        assertEquals(202, send("absent@auth143.test", "PASSWORD_RESET").statusCode())
        assertTrue(mail.messages.isEmpty())
    }

    @Test
    fun `daily quota counts all purposes and resets at the Seoul date boundary`() {
        val start = Instant.parse("2026-10-28T14:48:00Z")
        repeat(10) { index ->
            clock.set(start.plusSeconds(index * 60L))
            assertEquals(202, send("quota@auth143.test", VerificationPurpose.entries[index % 3].name).statusCode())
        }
        clock.set(Instant.parse("2026-10-28T14:58:00Z"))
        val limited = send("quota@auth143.test")
        assertError(limited, 429, "TOO_MANY_REQUESTS")
        assertEquals("2026-10-28T15:00:00Z", tree(limited).path("result").path("details").path("resendAvailableAt").asString())
        clock.set(Instant.parse("2026-10-28T15:00:00Z"))
        assertEquals(202, send("quota@auth143.test").statusCode())
        assertEquals(11, count())
    }

    @Test
    fun `concurrent first sends cannot bypass the interval and confirmation issues one token`() {
        Executors.newFixedThreadPool(8).use { pool ->
            val gate = CountDownLatch(1)
            val sends = (1..8).map { pool.submit(Callable { gate.await(5, TimeUnit.SECONDS); send("concurrent@auth143.test") }) }
            gate.countDown()
            val responses = sends.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(listOf(202, 429, 429, 429, 429, 429, 429, 429), responses.map { it.statusCode() }.sorted())
            assertEquals(1, count())
            assertEquals(1, mail.messages.size)
            val id = tree(responses.single { it.statusCode() == 202 }).path("result").path("verificationId").asString().toLong()
            val confirmGate = CountDownLatch(1)
            val confirmations = (1..2).map { pool.submit(Callable { confirmGate.await(5, TimeUnit.SECONDS); confirm(id, mail.messages.single()) }) }
            confirmGate.countDown()
            assertEquals(listOf(200, 410), confirmations.map { it.get(20, TimeUnit.SECONDS).statusCode() }.sorted())
        }
    }

    @Test
    fun `requests waiting for the verification lock do not hold pooled connections`() {
        val pool = jdbc.dataSource as HikariDataSource
        val lock = "SHA2(CONCAT(DATABASE(), ':auth-email-verifications'), 256)"
        var peak = 0
        pool.connection.use { holder ->
            holder.createStatement().use { it.executeQuery("SELECT GET_LOCK($lock, 0)") }
            Executors.newFixedThreadPool(12).use { requests ->
                val sends = (1..12).map { n -> requests.submit(Callable { send("waiting$n@auth143.test") }) }
                try {
                    // 모든 요청이 잠금 앞에 도착할 시간 동안 풀 사용량의 최댓값을 본다. 잠금을 잡은 연결 1개는 뺀다.
                    val deadline = System.nanoTime() + Duration.ofMillis(1500).toNanos()
                    while (System.nanoTime() < deadline) peak = maxOf(peak, pool.hikariPoolMXBean.activeConnections - 1)
                } finally {
                    // 이름 잠금은 연결 세션에 남는다. 풀로 돌려보내기 전에 반드시 푼다.
                    holder.createStatement().use { it.executeQuery("SELECT RELEASE_LOCK($lock)") }
                    sends.forEach { it.get(30, TimeUnit.SECONDS) }
                }
            }
        }
        assertTrue(peak <= 2, "waiting requests held $peak pooled connections")
    }

    @Test
    fun `active and withdrawal pending members reject signup but can receive reset codes`() {
        jdbc.update("INSERT INTO pitches (id,yul,octave,hanja,hangul,semitone) VALUES (990001,'검증',0,'黃','검증',50)")
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990001,'AUTH_TEST','검증',990001,990001,50)")
        jdbc.update("INSERT INTO users (id,email,password_hash,nickname,instrument_id,status) VALUES (990001,'active@auth143.test','test-hash','검증',990001,'ACTIVE')")
        jdbc.update("INSERT INTO users (id,email,password_hash,nickname,instrument_id,status,withdrawal_requested_at,purge_scheduled_at) VALUES (990002,'pending@auth143.test','test-hash','검증',990001,'WITHDRAWAL_PENDING','2026-10-28','2026-11-27')")
        for (email in listOf("active@auth143.test", "pending@auth143.test")) {
            assertError(send(email), 409, "EMAIL_ALREADY_EXISTS")
            assertEquals(202, send(email, "PASSWORD_RESET").statusCode())
        }
        assertEquals(2, mail.messages.size)
        clock.set(clock.instant().plusSeconds(60))
        mail.fail = true
        for (email in listOf("active@auth143.test", "pending@auth143.test", "absent@auth143.test")) {
            val reset = send(email, "PASSWORD_RESET")
            assertEquals(202, reset.statusCode(), reset.body())
            val id = tree(reset).path("result").path("verificationId").asString().toLong()
            assertError(confirm(id, "000000"), 400, "VERIFICATION_CODE_MISMATCH")
        }
    }

    private fun send(email: String, purpose: String = "SIGN_UP") = post("/api/auth/email-verifications", "{\"email\":\"$email\",\"purpose\":\"$purpose\"}")
    private fun confirm(id: Long, code: String) = post("/api/auth/email-verifications/$id/confirm", "{\"code\":\"$code\"}")
    private fun post(path: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString(),
    )
    private fun tree(response: HttpResponse<String>): JsonNode = json.readTree(response.body())
    private fun count() = jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email LIKE '%@auth143.test'", Int::class.java)
    private fun assertError(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertFalse(tree(response).path("isSuccess").asBoolean())
        assertEquals(code, tree(response).path("result").path("errorCode").asString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fixtures {
        @Bean @Primary fun verificationTestClock() = VerificationTestClock()
        @Bean @Primary fun capturingVerificationMail() = CapturingVerificationMail()
    }

    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String) = requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}

class VerificationTestClock : Clock() {
    private val value = AtomicReference(Instant.parse("2026-10-28T03:00:00Z"))
    fun set(now: Instant) { value.set(now) }
    override fun instant(): Instant = value.get()
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = fixed(instant(), zone)
}

class CapturingVerificationMail : VerificationMailSender {
    val messages = CopyOnWriteArrayList<String>()
    val recipients = CopyOnWriteArrayList<String>()
    @Volatile var fail = false
    override fun send(email: String, purpose: VerificationPurpose, code: String) {
        if (fail) throw VerificationMailUnavailable()
        recipients.add(email)
        messages.add(code)
    }
}

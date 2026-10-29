package com.ssafy.eolssu.backend.feature.auth

import com.nimbusds.jwt.SignedJWT
import com.ssafy.eolssu.backend.config.PasswordConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["eolssu.security.non-expiring-user-ids=990260"])
@Import(EmailVerificationApiDatabaseTests.Fixtures::class)
class NonExpiringAuthApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var clock: VerificationTestClock
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()

    @BeforeEach
    fun before() {
        cleanup()
        clock.set(Instant.parse("2000-01-01T00:00:00Z"))
        val hash = PasswordConfig().passwordEncoder().encode("Example!234")
        for (id in listOf(990260L, 990261L)) {
            jdbc.update("INSERT INTO users (id,email,password_hash,nickname,instrument_id,status) VALUES (?, ?, ?, '검증', 1, 'ACTIVE')", id, "$id@auth260.test", hash)
        }
    }

    @AfterEach
    fun after() { cleanup(); client.close() }

    private fun cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (990260,990261)")
        jdbc.update("DELETE FROM users WHERE id IN (990260,990261)")
    }

    @Test
    fun `only selected member has null expiration and old issued access still reaches protected API`() {
        val login = signIn(990260)
        val access = cookie(login, "access_token")
        val claims = SignedJWT.parse(access).jwtClaimsSet
        assertNull(claims.expirationTime)
        assertEquals(Instant.parse("2000-01-01T00:00:00Z").epochSecond, claims.issueTime.toInstant().epochSecond)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=990260 AND expires_at IS NULL", Int::class.java))
        assertEquals(200, request("GET", "/api/users/me", access).statusCode())
        assertTrue(login.headers().allValues("Set-Cookie").all { it.contains("Max-Age=34560000") })

        val ordinary = signIn(990261)
        val ordinaryClaims = SignedJWT.parse(cookie(ordinary, "access_token")).jwtClaimsSet
        assertEquals(1800, (ordinaryClaims.expirationTime.time - ordinaryClaims.issueTime.time) / 1000)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=990261 AND expires_at IS NOT NULL", Int::class.java))
        assertEquals(401, request("GET", "/api/users/me", cookie(ordinary, "access_token")).statusCode())
    }

    @Test
    fun `sign out can revoke permanent access even without refresh cookie`() {
        val login = signIn(990260)
        val access = cookie(login, "access_token")
        val refresh = cookie(login, "refresh_token")
        assertEquals(200, request("POST", "/api/auth/sign-out", access, body="{}").statusCode())
        assertEquals("SIGN_OUT", jdbc.queryForObject("SELECT revoke_reason FROM refresh_tokens WHERE user_id=990260", String::class.java))
        assertEquals(401, request("GET", "/api/users/me", access).statusCode())
        assertEquals(401, request("POST", "/api/auth/refresh", refresh=refresh, body="{}").statusCode())
    }

    @Test
    fun `refresh rotates permanent session and reuse rejects all its access tokens`() {
        val login = signIn(990260)
        val oldAccess = cookie(login, "access_token")
        val oldRefresh = cookie(login, "refresh_token")
        clock.set(Instant.parse("2050-01-01T00:00:00Z"))
        val rotated = request("POST", "/api/auth/refresh", refresh=oldRefresh, body="{}")
        assertEquals(200, rotated.statusCode(), rotated.body())
        assertTrue(rotated.body().contains("\"accessTokenExpiresAt\":null"), rotated.body())
        assertEquals(401, request("GET", "/api/users/me", oldAccess).statusCode())
        val access = cookie(rotated, "access_token")
        assertEquals(200, request("GET", "/api/users/me", access).statusCode())
        assertEquals(401, request("POST", "/api/auth/refresh", refresh=oldRefresh, body="{}").statusCode())
        assertEquals(401, request("GET", "/api/users/me", access).statusCode())
    }

    @Test
    fun `password change and withdrawal revoke previous permanent access`() {
        val login = signIn(990260)
        val oldAccess = cookie(login, "access_token")
        val changed = request("PATCH", "/api/users/me/password", oldAccess, body="""{"currentPassword":"Example!234","newPassword":"Changed!567"}""")
        assertEquals(200, changed.statusCode(), changed.body())
        assertEquals(401, request("GET", "/api/users/me", oldAccess).statusCode())
        val access = cookie(changed, "access_token")
        assertEquals(200, request("GET", "/api/users/me", access).statusCode())
        val withdrawn = request("DELETE", "/api/users/me", access, body="""{"currentPassword":"Changed!567"}""")
        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(401, request("GET", "/api/users/me", access).statusCode())
    }

    private fun signIn(id: Long): HttpResponse<String> {
        val response = request("POST", "/api/auth/sign-in", body="""{"email":"$id@auth260.test","password":"Example!234"}""")
        assertEquals(200, response.statusCode(), response.body())
        return response
    }

    private fun cookie(response: HttpResponse<String>, name: String) =
        response.headers().allValues("Set-Cookie").first { it.startsWith("$name=") }.substringAfter('=').substringBefore(';')

    private fun request(method: String, path: String, access: String? = null, refresh: String? = null, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type", "application/json")
        val cookies = listOfNotNull(access?.let { "access_token=$it" }, refresh?.let { "refresh_token=$it" })
        if (cookies.isNotEmpty()) builder.header("Cookie", cookies.joinToString("; "))
        return client.send(builder.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("eolssu.security.jwt-secret") { "test-signing-key-at-least-32-bytes-long!!" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}

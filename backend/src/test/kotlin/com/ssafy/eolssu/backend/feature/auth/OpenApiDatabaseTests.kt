package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.feature.auth.domain.AccessTokenSigner
import java.net.URI
import java.net.http.*
import java.time.Instant
import kotlin.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmailVerificationApiDatabaseTests.Fixtures::class)
open class OpenApiDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var signer: AccessTokenSigner
    @Value("\${local.server.port}") private var port = 0
    private val client = HttpClient.newHttpClient()
    protected val json = JsonMapper.builder().build()
    @BeforeEach fun before() {
        cleanup()
        jdbc.update("INSERT INTO pitches(id,yul,octave,hanja,hangul,semitone) VALUES (990253,'문서',0,'黃','문서',50)")
        jdbc.update("INSERT INTO instruments(id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990253,'DOC253','문서',990253,990253,50)")
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,instrument_id,status) VALUES (990253,'docs@example.test','unused','문서',990253,'ACTIVE')")
    }
    @AfterEach fun after() { client.close(); cleanup() }
    private fun cleanup() {
        jdbc.update("DELETE FROM users WHERE id=990253")
        jdbc.update("DELETE FROM instruments WHERE id=990253")
        jdbc.update("DELETE FROM pitches WHERE id=990253")
    }
    protected fun get(path: String, authenticated: Boolean=true): HttpResponse<String> {
        val request=HttpRequest.newBuilder(URI("http://localhost:$port$path"))
        if (authenticated) {
            val now=Instant.now()
            request.header("Cookie","access_token=${signer.sign(990253,now,now.plusSeconds(1800))}")
        }
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString())
    }
    @Test fun `authenticated user can fetch the full business API definition`() {
        val response=get("/api/v3/api-docs")
        assertEquals(200,response.statusCode(),response.body())
        val document=json.readTree(response.body())
        assertTrue(document.path("openapi").asString().startsWith("3."))
        assertTrue(document.path("paths").has("/api/auth/password-reset"))
    }
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") private lateinit var mappings: org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
    @Test fun `definition covers every implemented method with schemas errors and cookie security`() {
        val response=get("/api/v3/api-docs")
        assertEquals(200,response.statusCode(),response.body())
        val doc=json.readTree(response.body())
        val actual=doc.path("paths").properties().flatMap { (path,item) ->
            item.properties().filter { it.key in setOf("get","post","patch","put","delete") }.map { "${it.key} $path" }
        }.toSet()
        val expected=mappings.handlerMethods.filterValues { it.beanType.packageName.startsWith("com.ssafy.eolssu.backend.feature.") }
            .flatMap { (mapping,_) -> mapping.patternValues.flatMap { path -> mapping.methodsCondition.methods.map { "${it.name.lowercase()} ${path.replace("{version:\\d{4}-\\d{2}-\\d{2}}", "{version}")}" } } }.toSet()
        fun checkReferences(node: tools.jackson.databind.JsonNode) {
            val ref=node.path("\$ref").asString()
            if (ref.startsWith("#/components/schemas/")) assertTrue(doc.path("components").path("schemas").has(ref.substringAfterLast('/')),ref)
            for (child in node) checkReferences(child)
        }
        checkReferences(doc)
        assertEquals(52,expected.size)
        val catalogPaths = setOf("/api/instruments", "/api/songs/{songId}/score", "/api/songs/{songId}/model-audio",
            "/api/pitches", "/api/instruments/{instrumentId}/fingerings", "/api/jangdans/{jangdanId}",
            "/api/notation-symbols", "/api/jangdans")
        assertTrue(expected.containsAll(catalogPaths.map { "get $it" }))
        assertEquals(expected,actual)
        for (path in catalogPaths) {
            val operation=doc.path("paths").path(path).path("get")
            assertEquals(0,operation.path("security").size(),path)
            val envelope=resolve(doc,operation.path("responses").path("200").path("content").path("application/json").path("schema"))
            assertTrue(envelope.path("properties").has("result"),path)
        }
        val modelAudio=doc.path("components").path("schemas").path("ModelAudioResult")
        assertEquals("string",modelAudio.path("properties").path("songId").path("type").asString())
        assertEquals("integer",modelAudio.path("properties").path("durationMs").path("type").asString())
        assertEquals("integer",modelAudio.path("properties").path("scoreVersion").path("type").asString())
        for (entry in actual) {
            val (method,path)=entry.split(' ',limit=2)
            // HTML 약관·서명된 녹음 파일 응답은 업무 JSON envelope 계약과 별도다. 경로 포함 여부는 위에서 비교한다.
            if(path.startsWith("/api/terms/documents/") || path=="/api/recordings/read") continue
            val operation=doc.path("paths").path(path).path(method)
            assertTrue(operation.path("summary").asString().isNotBlank(),entry)
            assertTrue(operation.path("responses").has(if(path=="/api/webhooks/kakao/events")"503" else "500"),entry)
            assertTrue(operation.has("security"),entry)
        }
        val signup=doc.path("paths").path("/api/auth/oauth/sign-up").path("post")
        assertEquals(0,signup.path("security").size())
        assertTrue(signup.path("responses").has("201"))
        assertTrue(signup.path("responses").has("410"))
        for(path in listOf("/api/users/me/withdrawal/oauth/{provider}/start","/api/users/me/withdrawal/oauth/{provider}")) {
            val operation=doc.path("paths").path(path).path("post")
            assertTrue(operation.path("security").any{it.has("accessCookie")},path)
        }
        val schemes=doc.path("components").path("securitySchemes")
        val webhook=doc.path("paths").path("/api/webhooks/kakao/events").path("post")
        assertEquals(0,webhook.path("security").size())
        assertFalse(webhook.path("responses").path("202").has("content"))
        assertTrue(webhook.path("requestBody").path("content").has("application/secevent+jwt"))
        assertTrue(webhook.path("responses").path("400").path("content").path("application/json").path("schema").path("properties").has("err"))
        assertEquals("cookie",schemes.path("accessCookie").path("in").asString())
        assertEquals("access_token",schemes.path("accessCookie").path("name").asString())
        assertEquals("refresh_token",schemes.path("refreshCookie").path("name").asString())
        val reset=doc.path("paths").path("/api/auth/password-reset").path("post")
        assertTrue(reset.path("responses").has("204"))
        assertFalse(reset.path("responses").path("204").has("content"))
        assertEquals(0,reset.path("security").size())
        assertTrue(reset.path("responses").has("401"))
        for (path in listOf("/api/auth/refresh","/api/auth/sign-out","/api/practice-sessions/{sessionId}/retry")) {
            val body=doc.path("paths").path(path).path("post").path("requestBody")
            assertFalse(body.path("required").asBoolean(),path)
            assertTrue(body.path("content").has("application/json"),path)
        }
        val login=doc.path("paths").path("/api/auth/sign-in").path("post")
        val loginSchema=resolve(doc,login.path("responses").path("200").path("content").path("application/json").path("schema"))
        assertEquals(setOf("httpStatus","isSuccess","message","code","result"),loginSchema.path("properties").properties().map { it.key }.toSet())
        val result=resolve(doc,loginSchema.path("properties").path("result"))
        assertEquals("string",result.path("properties").path("userId").path("type").asString())
        assertTrue(result.path("properties").has("withdrawalCancelled"))
        val upload=doc.path("paths").path("/api/practice-sessions/{sessionId}/recording").path("post")
        assertTrue(upload.path("responses").has("413"))
        val multipart=resolve(doc,upload.path("requestBody").path("content").path("multipart/form-data").path("schema"))
        assertEquals("binary",multipart.path("properties").path("audio").path("format").asString())
        val details=doc.path("components").path("schemas").path("ApiErrorResult")
        assertTrue(details.path("properties").has("details"))
        assertEquals(6,details.path("properties").path("details").path("oneOf").size())
        assertFalse(details.path("required").any { it.asString() == "details" })
        val session=doc.path("components").path("schemas").path("PracticeSessionResult")
        assertTrue(session.path("properties").path("evaluationId").path("nullable").asBoolean(),session.toString())
        val song = doc.path("components").path("schemas").path("SongItem")
        assertTrue(song.path("properties").path("jangdan").path("nullable").asBoolean(), song.toString())
        val request=doc.path("components").path("schemas").path("PasswordResetRequest")
        assertEquals(8,request.path("properties").path("newPassword").path("minLength").asInt())
        assertEquals(128,request.path("properties").path("newPassword").path("maxLength").asInt())
        assertTrue(request.path("required").any { it.asString()=="verificationToken" })
        val score=doc.path("components").path("schemas").path("RecordScoreViewRequest")
        assertEquals("string",score.path("properties").path("songId").path("type").asString())
    }
    @Test fun `documentation is public while member APIs still require an active member`() {
        for (path in listOf("/api/v3/api-docs","/api/v3/api-docs/swagger-config","/api/swagger-ui/index.html")) {
            assertEquals(200,get(path,false).statusCode(),path)
        }
        val entry=get("/api/swagger-ui.html",false)
        assertTrue(entry.statusCode() in setOf(301,302,307,308),entry.body())
        val location=entry.headers().firstValue("Location").orElseThrow()
        assertTrue(URI(location).path.startsWith("/api/swagger-ui/"),location)
        val html=get(URI(location).rawPath,false)
        assertEquals(200,html.statusCode(),html.body())
        assertTrue(html.body().contains("swagger-ui"))
        for (asset in listOf("swagger-ui.css","swagger-ui-bundle.js","swagger-initializer.js")) {
            assertEquals(200,get("/api/swagger-ui/$asset",false).statusCode(),asset)
            assertEquals(200,get("/api/swagger-ui/$asset").statusCode(),asset)
        }
        jdbc.update("UPDATE users SET status='WITHDRAWAL_PENDING',withdrawal_requested_at='2026-10-28',purge_scheduled_at='2026-11-27' WHERE id=990253")
        assertEquals(200,get("/api/v3/api-docs").statusCode())
        assertEquals(401,get("/api/users/me").statusCode())
        assertEquals(401,get("/api/users/me",false).statusCode())
        jdbc.update("UPDATE users SET status='ACTIVE',withdrawal_requested_at=NULL,purge_scheduled_at=NULL WHERE id=990253")
        val config=get("/api/v3/api-docs/swagger-config",false)
        assertEquals(200,config.statusCode(),config.body())
        val uiConfig=json.readTree(config.body())
        assertEquals("/api/v3/api-docs",uiConfig.path("url").asString())
        assertTrue(uiConfig.path("withCredentials").asBoolean())
        assertTrue(uiConfig.has("validatorUrl"),config.body())
        assertEquals("",uiConfig.path("validatorUrl").asString())
    }
    private fun resolve(doc: tools.jackson.databind.JsonNode,schema: tools.jackson.databind.JsonNode): tools.jackson.databind.JsonNode {
        val ref=schema.path("\$ref").asString()
        return if (ref.startsWith("#/components/schemas/")) doc.path("components").path("schemas").path(ref.substringAfterLast('/')) else schema
    }
    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("eolssu.security.jwt-secret") { "test-signing-key-at-least-32-bytes-long!!" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String)=requireNotNull(System.getenv(name))
    }
}

/** 배포 프로필에서도 문서는 공개되고 회원 API는 보호되는지 검증한다. */
@ActiveProfiles("ops", inheritProfiles = false)
class OpenApiOpsDatabaseTests : OpenApiDatabaseTests()

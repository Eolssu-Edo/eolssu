package com.ssafy.eolssu.backend.feature.growth.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.growth.application.RecordScoreView
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewReferenceMissing
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewSegment
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ScoreViewControllerTests {
    private val repository = MemoryScoreViewRepository(songIds = setOf(12))
    private val mvc = MockMvcBuilders
        .standaloneSetup(ScoreViewController(RecordScoreView(repository, Clock.fixed(Instant.parse("2026-10-28T05:05:01Z"), ZoneOffset.UTC))))
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .build()

    @BeforeEach
    fun signIn() = signInAs(7)

    @AfterEach
    fun signOut() = SecurityContextHolder.clearContext()

    @Test
    fun `stores a new segment for the signed-in member and returns 201 with the stored segment`() {
        send().andExpect(status().isCreated).andExpect(content().json(
            """{"httpStatus":"CREATED","isSuccess":true,"message":"체류 기록 저장 성공","code":201,
            "result":{"songId":"12","startedAt":"2026-10-28T05:00:00Z","endedAt":"2026-10-28T05:05:00Z","visibleSeconds":300,"countedSeconds":240}}""",
            true,
        ))
        assertEquals(listOf(7L to "key-1"), repository.rows.map { it.userId to it.idempotencyKey })
    }

    @Test
    fun `returns 200 for the same key and body without storing again`() {
        send()
        send().andExpect(status().isOk)
            .andExpect(jsonPath("$.httpStatus").value("OK"))
            .andExpect(jsonPath("$.message").value("이미 저장된 체류 기록"))
            .andExpect(jsonPath("$.result.countedSeconds").value(240))
        assertEquals(1, repository.rows.size)
    }

    @Test
    fun `keeps the key separate per member`() {
        send()
        signInAs(8)
        send().andExpect(status().isCreated)
        assertEquals(listOf(7L, 8L), repository.rows.map { it.userId })
    }

    @Test
    fun `returns 409 for the same key with a different body`() {
        send()
        send(body(countedSeconds = 200)).andExpect(status().isConflict)
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.result.errorCode").value("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `returns 400 with the rejection reason for an invalid segment`() {
        send(body(countedSeconds = 301)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.result.details.reason").value("COUNTED_EXCEEDS_VISIBLE"))
        assertEquals(emptyList(), repository.rows)
    }

    @Test
    fun `returns 400 for a song ID that is not a positive integer string`() {
        for (songId in listOf("\"abc\"", "\"0\"", "\"-1\"", "\"99999999999999999999\"", "12")) {
            send(body(songId = songId)).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test
    fun `returns 400 without the idempotency key or with a malformed body`() {
        send(key = null).andExpect(status().isBadRequest).andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        send("""{"songId":"12"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        send(body(startedAt = "\"yesterday\"")).andExpect(status().isBadRequest)
        send(key = "has space").andExpect(status().isBadRequest).andExpect(jsonPath("$.result.details.reason").value("INVALID_KEY"))
        assertEquals(emptyList(), repository.rows)
    }

    @Test
    fun `accepts only ISO-8601 strings for times and converts offsets to UTC`() {
        for (startedAt in listOf("1792990800", "1e300", "1792990800.5", "null", "\"2026-10-28 05:00:00\"")) {
            send(body(startedAt = startedAt)).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        assertEquals(emptyList(), repository.rows)

        send(body(startedAt = "\"2026-10-28T14:00:00+09:00\"")).andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.startedAt").value("2026-10-28T05:00:00Z"))
    }

    @Test
    fun `accepts only JSON integers for seconds`() {
        for (seconds in listOf("239.9", "\"240\"", "2.4e2", "true", "2147483648", "null")) {
            send(body(countedSeconds = seconds)).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        send(body().replace(""","countedSeconds":240""", "")).andExpect(status().isBadRequest)
        assertEquals(emptyList(), repository.rows)
    }

    @Test
    fun `returns 401 when the token member has no row`() {
        repository.missingUserIds += 7
        send().andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result.errorCode").value("UNAUTHENTICATED"))
        assertEquals(emptyList(), repository.rows)
    }

    @Test
    fun `returns 404 for an unknown song`() {
        send(body(songId = "\"99\"")).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
    }

    private fun send(json: String = body(), key: String? = "key-1"): ResultActions = mvc.perform(
        post("/api/users/me/score-views").contentType(MediaType.APPLICATION_JSON).content(json)
            .apply { if (key != null) header("Idempotency-Key", key) },
    )

    private fun body(songId: String = "\"12\"", startedAt: String = "\"2026-10-28T05:00:00Z\"", countedSeconds: Any = 240) =
        """{"songId":$songId,"startedAt":$startedAt,"endedAt":"2026-10-28T05:05:00Z","visibleSeconds":300,"countedSeconds":$countedSeconds}"""

    private fun signInAs(userId: Long) {
        val jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(userId.toString()).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    private class MemoryScoreViewRepository(private val songIds: Set<Long>) : ScoreViewRepository {
        val rows = mutableListOf<ScoreViewSegment>()
        val missingUserIds = mutableSetOf<Long>()

        override fun songExists(songId: Long) = songId in songIds

        override fun findByKey(userId: Long, idempotencyKey: String) =
            rows.find { it.userId == userId && it.idempotencyKey == idempotencyKey }

        override fun findLatestByKey(userId: Long, idempotencyKey: String) = findByKey(userId, idempotencyKey)

        override fun insertIfAbsent(segment: ScoreViewSegment): Boolean {
            if (segment.userId in missingUserIds) throw ScoreViewReferenceMissing()
            return findByKey(segment.userId, segment.idempotencyKey) == null && rows.add(segment)
        }
    }
}

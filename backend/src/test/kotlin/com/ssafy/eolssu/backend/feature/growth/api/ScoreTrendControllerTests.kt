package com.ssafy.eolssu.backend.feature.growth.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.growth.application.GetScoreTrend
import com.ssafy.eolssu.backend.feature.growth.domain.PlayedSong
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreScope
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreTrendRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoredPlay
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ScoreTrendControllerTests {
    private val repository = RecordingRepository()

    /** 지금 악보는 버전 2이고 음표 4개가 각 0·0·1·1에 있다. */
    private val mvc = MockMvcBuilders
        .standaloneSetup(ScoreTrendController(GetScoreTrend(repository) { _, version -> if (version == 2) listOf(0, 0, 1, 1) else null }))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    // 실제 토큰 검증은 보안 필터 검사가 맡는다. 여기서는 검증을 통과한 회원 7로 둔다.
    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "HS256").subject("7").build())
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `returns the latest plays of the most recently played song oldest first`() {
        repository.songs += PlayedSong(2, "도라지", Instant.parse("2026-10-30T00:00:00Z"), 3, 1)
        repository.songs += PlayedSong(1, "아리랑", Instant.parse("2026-10-20T00:00:00Z"), 1, 0)
        repository.plays += ScoredPlay(41, Instant.parse("2026-10-29T00:00:00Z"), 72, "QUARTER", 0, 3, 2, "pyin-4", 80, 81, 82, 83)
        repository.plays += ScoredPlay(40, Instant.parse("2026-10-28T00:00:00Z"), 66, "DOTTED_QUARTER", 0, 3, 1, "pyin-3", 70, 71, 72, 73)

        mvc.perform(get("/api/users/me/growth/scores"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"message":"최근 점수 변화 조회 성공","code":200,"result":{
                    "songId":"2","scope":"FULL","comparable":false,
                    "items":[
                    {"sessionId":"40","startedAt":"2026-10-28T00:00:00Z","bpm":66,"bpmUnit":"DOTTED_QUARTER","startNoteIdx":0,"endNoteIdx":3,
                    "startColumnIdx":null,"endColumnIdx":null,"scoreVersion":1,"modelVersion":"pyin-3",
                    "totalScore":70,"pitchScore":71,"timingScore":72,"durationScore":73},
                    {"sessionId":"41","startedAt":"2026-10-29T00:00:00Z","bpm":72,"bpmUnit":"QUARTER","startNoteIdx":0,"endNoteIdx":3,
                    "startColumnIdx":0,"endColumnIdx":1,"scoreVersion":2,"modelVersion":"pyin-4",
                    "totalScore":80,"pitchScore":81,"timingScore":82,"durationScore":83}],
                    "songs":[
                    {"songId":"2","title":"도라지","lastPlayedAt":"2026-10-30T00:00:00Z","fullPlayCount":3,"sectionPlayCount":1},
                    {"songId":"1","title":"아리랑","lastPlayedAt":"2026-10-20T00:00:00Z","fullPlayCount":1,"sectionPlayCount":0}]}}""",
                    JsonCompareMode.STRICT,
                ),
            )
        assertEquals(listOf(Call(7, 2, ScoreScope.FULL, 5)), repository.calls)
    }

    @Test
    fun `passes the requested song, scope and limit`() {
        mvc.perform(get("/api/users/me/growth/scores?songId=12&scope=SECTION&limit=100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.songId").value("12"))
            .andExpect(jsonPath("$.result.scope").value("SECTION"))
        mvc.perform(get("/api/users/me/growth/scores?limit=1")).andExpect(status().isOk)

        assertEquals(listOf(Call(7, 12, ScoreScope.SECTION, 100)), repository.calls) // 두 번째는 곡이 없어 조회하지 않는다
    }

    @Test
    fun `answers that there is nothing to show yet`() {
        mvc.perform(get("/api/users/me/growth/scores?scope=SECTION"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"result":{"songId":null,"scope":"SECTION","items":[],"comparable":false,"songs":[]}}""",
                    JsonCompareMode.LENIENT,
                ),
            )
    }

    @Test
    fun `rejects malformed queries before reading anything`() {
        val queries = listOf(
            "limit=0", "limit=101", "limit=abc", "limit=", "limit=5.0", "limit=05", "limit=+5",
            "scope=full", "scope=ALL", "scope=",
            "songId=0", "songId=-1", "songId=abc", "songId=", "songId=99999999999999999999", "songId=05", "songId=+1",
        )
        for (query in queries) {
            mvc.perform(get("/api/users/me/growth/scores?$query"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        assertEquals(0, repository.songReads)
    }

    private data class Call(val userId: Long, val songId: Long, val scope: ScoreScope, val limit: Int)

    private class RecordingRepository : ScoreTrendRepository {
        val songs = mutableListOf<PlayedSong>()

        /** 최근 순. */
        val plays = mutableListOf<ScoredPlay>()
        val calls = mutableListOf<Call>()
        var songReads = 0

        override fun playedSongs(userId: Long): List<PlayedSong> {
            songReads++
            return songs
        }

        override fun recentScoredPlays(userId: Long, songId: Long, scope: ScoreScope, limit: Int): List<ScoredPlay> {
            calls += Call(userId, songId, scope, limit)
            return plays.take(limit)
        }
    }
}

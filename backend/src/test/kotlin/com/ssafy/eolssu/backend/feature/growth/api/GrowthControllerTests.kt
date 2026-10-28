package com.ssafy.eolssu.backend.feature.growth.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.growth.application.GetGrowthStats
import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class GrowthControllerTests {
    private val repository = RecordingRepository()
    private val mvc = MockMvcBuilders
        .standaloneSetup(GrowthController(GetGrowthStats(repository, Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC))))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    // 실제 토큰 검증은 보안 필터 검사(AccessTokenSecurityTests)가 맡는다. 여기서는 검증을 통과한 회원 7로 둔다.
    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "HS256").subject("7").build())
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `returns the summary of the signed in member with null missions`() {
        repository.seconds[LocalDate.parse("2026-10-27")] = 144
        repository.sessions[LocalDate.parse("2026-10-20")] = SessionTotals(2, 2, 146)

        mvc.perform(get("/api/users/me/growth/summary?from=2026-10-26&to=2026-11-01"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"httpStatus":"OK","isSuccess":true,"code":200,"result":{"from":"2026-10-26","to":"2026-11-01",
                    "practiceCount":0,"practiceSeconds":144,"practiceDays":1,"currentStreakDays":1,"averageTotalScore":null,
                    "previousPeriod":{"practiceSeconds":0,"practiceCount":2,"averageTotalScore":73},
                    "completedMissionCount":null,"totalMissionCount":null}}""",
                ),
            )
        assertEquals(setOf(7L), repository.users)
        assertEquals(1, repository.calls)
    }

    @Test
    fun `returns every day of the calendar`() {
        repository.seconds[LocalDate.parse("2026-10-27")] = 96
        repository.sessions[LocalDate.parse("2026-10-27")] = SessionTotals(2, 2, 150)

        mvc.perform(get("/api/users/me/growth/calendar?from=2026-10-26&to=2026-10-28"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """{"result":{"items":[{"date":"2026-10-26","practiceSeconds":0,"practiceCount":0},
                    {"date":"2026-10-27","practiceSeconds":96,"practiceCount":2},{"date":"2026-10-28","practiceSeconds":0,"practiceCount":0}]}}""",
                ),
            )
    }

    @Test
    fun `rejects missing, malformed, reversed and too long ranges`() {
        val queries = listOf(
            "from=2026-10-26", "from=2026-10-26&to=2026/11/01", "from=2026-11-01&to=2026-10-31",
            "from=2026-01-01&to=2027-01-02", // 367일
            // DB DATETIME 범위(1000~9999년)와 지난 기간 계산을 넘는 날짜
            "from=-999999999-01-01&to=-999999999-01-01", "from=1000-12-31&to=1000-12-31", "from=9999-01-01&to=9999-01-01",
        )
        for (path in listOf("summary", "calendar")) {
            for (query in queries) {
                mvc.perform(get("/api/users/me/growth/$path?$query"))
                    .andExpect(status().isBadRequest)
                    .andExpect(content().json("""{"isSuccess":false,"code":400,"result":{"errorCode":"INVALID_REQUEST"}}"""))
            }
        }
        // 366일(2028년 윤년)은 허용한다.
        mvc.perform(get("/api/users/me/growth/calendar?from=2028-01-01&to=2028-12-31")).andExpect(status().isOk)
        assertEquals(1, repository.calls)
    }

    private class RecordingRepository : PracticeStatsRepository {
        val seconds = mutableMapOf<LocalDate, Long>()
        val sessions = mutableMapOf<LocalDate, SessionTotals>()
        val users = mutableSetOf<Long>()
        var calls = 0

        override fun dailyPracticeSeconds(userId: Long, range: DateRange) =
            seconds.filterKeys { it in range }.also { users += userId; calls++ }

        override fun dailyDoneSessions(userId: Long, range: DateRange) = sessions.filterKeys { it in range }

        override fun practiceDaysUpTo(userId: Long, lastDay: LocalDate, minSeconds: Long) =
            seconds.filter { (day, total) -> day <= lastDay && total >= minSeconds }.keys.sortedDescending()
    }
}

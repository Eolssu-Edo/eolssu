package com.ssafy.eolssu.backend.feature.growth.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.growth.application.GetGrowthStats
import com.ssafy.eolssu.backend.feature.growth.application.ScoreChange
import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import java.time.LocalDate
import org.springframework.context.annotation.Profile
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 연습 기록지(GROWTH-01) API. Notion API 명세 GRO-01 summary, GRO-02 calendar. */
@RestController
@RequestMapping("/api/users/me/growth")
@Profile("db | ops")
class GrowthController(private val stats: GetGrowthStats) {
    @GetMapping("/summary")
    fun summary(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<*> {
        val range = rangeOf(from, to) ?: return invalidRequest()
        val summary = stats.summary(jwt.userId(), range)
        return ok(
            "연습 기록지 요약 조회 성공",
            SummaryResult(
                from = range.start.toString(),
                to = range.endInclusive.toString(),
                practiceCount = summary.totals.practiceCount,
                practiceSeconds = summary.totals.practiceSeconds,
                practiceDays = summary.practiceDays,
                currentStreakDays = summary.currentStreakDays,
                averageTotalScore = summary.totals.averageTotalScore,
                previousPeriod = PreviousPeriod(
                    summary.previous.practiceSeconds, summary.previous.practiceCount, summary.previous.averageTotalScore,
                ),
                // 미션 미구현: 0으로 꾸미지 않고 null(명세 규칙 5).
                completedMissionCount = null,
                totalMissionCount = null,
                bestBySong = summary.bestBySong.map { BestSong(it.songId.toString(), it.title, it.bestTotalScore) },
                metricGrowth = summary.metricGrowth.let { growth ->
                    MetricGrowthResult(
                        growth.songId?.toString(), growth.title, growth.bpm,
                        growth.pitch.toResult(), growth.timing.toResult(), growth.duration.toResult(),
                    )
                },
            ),
        )
    }

    @GetMapping("/calendar")
    fun calendar(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<*> {
        val range = rangeOf(from, to) ?: return invalidRequest()
        return ok(
            "날짜별 연습 시간·횟수 조회 성공",
            CalendarResult(stats.calendar(jwt.userId(), range).map { CalendarItem(it.date.toString(), it.practiceSeconds, it.practiceCount) }),
        )
    }

    private fun ScoreChange.toResult() = ScoreChangeResult(first, last)

    // 양 끝 포함, 최대 366일. 연도는 DB DATETIME 범위(1000~9999년) 안에서 지난 기간까지 계산할 수 있게 제한한다.
    private fun rangeOf(from: LocalDate, to: LocalDate): DateRange? =
        if (from > to || from.year < MIN_YEAR || to.year > MAX_YEAR) null
        else DateRange(from, to).takeIf { it.length <= GetGrowthStats.MAX_RANGE_DAYS }

    // 토큰 검증에서 subject가 양의 정수인지 확인한다(AccessTokenConfig).
    private fun Jwt.userId() = requireNotNull(subject).toLong()

    private fun <T> ok(message: String, result: T) = ResponseEntity.ok(GrowthResponse("OK", true, message, 200, result))

    private companion object {
        const val MIN_YEAR = 1001
        const val MAX_YEAR = 9998
    }

    private fun invalidRequest() = ResponseEntity.status(ErrorCode.INVALID_REQUEST.status).body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))
}

data class GrowthResponse<T>(val httpStatus: String, @get:JsonProperty("isSuccess") val isSuccess: Boolean, val message: String, val code: Int, val result: T)

data class SummaryResult(
    val from: String,
    val to: String,
    val practiceCount: Long,
    val practiceSeconds: Long,
    val practiceDays: Int,
    val currentStreakDays: Int,
    val averageTotalScore: Int?,
    val previousPeriod: PreviousPeriod,
    val completedMissionCount: Int?,
    val totalMissionCount: Int?,
    val bestBySong: List<BestSong>,
    val metricGrowth: MetricGrowthResult,
)

data class BestSong(val songId: String, val title: String, val bestTotalScore: Int)

data class MetricGrowthResult(
    val songId: String?,
    val title: String?,
    val bpm: Int?,
    val pitch: ScoreChangeResult,
    val timing: ScoreChangeResult,
    val duration: ScoreChangeResult,
)

data class ScoreChangeResult(val first: Int?, val last: Int?)

data class PreviousPeriod(val practiceSeconds: Long, val practiceCount: Long, val averageTotalScore: Int?)

data class CalendarResult(val items: List<CalendarItem>)

data class CalendarItem(val date: String, val practiceSeconds: Long, val practiceCount: Long)

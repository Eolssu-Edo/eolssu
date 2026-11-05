package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * 연습 기록지 통계(GRO-01·02)와 곡별 점수 변화(GRO-03)를 앱 공통 HTTP 클라이언트로 조회한다. 날짜는 `YYYY-MM-DD` KST 날짜로 보낸다.
 * 서버의 음정·박자·음 길이(`pitch`·`timing`·`duration`)는 평가 항목 [EvaluationMetric]의 Pitch·Rhythm·Length다.
 */
class HttpGrowthRepository(private val client: HttpClient, private val member: SignedInMember? = null) : GrowthRepository {
    override suspend fun summary(fromDay: Long, toDay: Long): GrowthSummary? =
        member.inSession { fetch<SummaryDto>("/api/users/me/growth/summary", fromDay, toDay)?.toModel() }

    override suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>? =
        member.inSession { fetch<CalendarDto>("/api/users/me/growth/calendar", fromDay, toDay)?.items?.mapNotNull { it.toModel() } }

    override suspend fun scoreTrend(songId: String?, scope: TrendScope, limit: Int): ScoreTrend? = member.inSession {
        val response = client.sendOrNull {
            get("/api/users/me/growth/scores") {
                songId?.let { parameter("songId", it) }
                parameter("scope", if (scope == TrendScope.Section) "SECTION" else "FULL")
                parameter("limit", limit)
            }
        } ?: return@inSession null
        if (!response.status.isSuccess()) return@inSession null
        response.apiResponse<ScoreTrendDto>()?.result?.toModel()
    }

    private suspend inline fun <reified T> fetch(path: String, fromDay: Long, toDay: Long): T? {
        val response = client.sendOrNull {
            get(path) {
                parameter("from", isoDate(fromDay))
                parameter("to", isoDate(toDay))
            }
        } ?: return null
        if (!response.status.isSuccess()) return null
        return response.apiResponse<T>()?.result
    }

    @Serializable
    private class SummaryDto(
        val practiceCount: Long,
        val practiceSeconds: Long,
        val practiceDays: Int,
        val currentStreakDays: Int,
        val averageTotalScore: Int? = null,
        val previousPeriod: PreviousPeriodDto,
        val bestBySong: List<BestSongDto> = emptyList(),
        val metricGrowth: MetricGrowthDto? = null,
    ) {
        fun toModel() = GrowthSummary(
            current = PeriodTotals(practiceSeconds, practiceCount, averageTotalScore),
            previous = PeriodTotals(previousPeriod.practiceSeconds, previousPeriod.practiceCount, previousPeriod.averageTotalScore),
            practiceDays = practiceDays,
            currentStreakDays = currentStreakDays,
            bestBySong = bestBySong.map { SongBestScore(it.songId, it.title, it.bestTotalScore) },
            metricGrowth = metricGrowth?.toModel(),
        )
    }

    @Serializable
    private class PreviousPeriodDto(val practiceSeconds: Long, val practiceCount: Long, val averageTotalScore: Int? = null)

    @Serializable
    private class BestSongDto(val songId: String, val title: String, val bestTotalScore: Int)

    /** 전체 곡 연주가 없으면 곡·BPM이 null이다. */
    @Serializable
    private class MetricGrowthDto(
        val songId: String? = null,
        val title: String? = null,
        val bpm: Int? = null,
        val pitch: ScoreChangeDto = ScoreChangeDto(),
        val timing: ScoreChangeDto = ScoreChangeDto(),
        val duration: ScoreChangeDto = ScoreChangeDto(),
    ) {
        fun toModel(): MetricGrowth? {
            if (songId == null || title == null || bpm == null) return null
            val changes = listOf(EvaluationMetric.Pitch to pitch, EvaluationMetric.Rhythm to timing, EvaluationMetric.Length to duration)
                .mapNotNull { (metric, change) -> if (change.first != null && change.last != null) MetricChange(metric, change.first, change.last) else null }
            return MetricGrowth(songId, title, bpm, changes)
        }
    }

    @Serializable
    private class ScoreChangeDto(val first: Int? = null, val last: Int? = null)

    /** GRO-03 `result`. 모르는 `scope`는 FULL로 읽는다. 시각을 읽지 못한 점은 뺀다. */
    @Serializable
    private class ScoreTrendDto(
        val songId: String? = null,
        val scope: String = "FULL",
        val items: List<TrendItemDto> = emptyList(),
        val comparable: Boolean = false,
        val songs: List<TrendSongDto> = emptyList(),
    ) {
        fun toModel() = ScoreTrend(
            songId = songId,
            scope = if (scope == "SECTION") TrendScope.Section else TrendScope.Full,
            plays = items.mapNotNull { it.toModel() },
            comparable = comparable,
            songs = songs.map { TrendSong(it.songId, it.title, it.fullPlayCount, it.sectionPlayCount) },
        )
    }

    @Serializable
    private class TrendItemDto(
        val sessionId: String,
        val startedAt: String,
        val bpm: Int,
        val startColumnIdx: Int? = null,
        val endColumnIdx: Int? = null,
        val totalScore: Int,
    ) {
        fun toModel(): TrendPlay? {
            val millis = runCatching { Instant.parse(startedAt).toEpochMilliseconds() }.getOrNull() ?: return null
            return TrendPlay(sessionId, millis, bpm, startColumnIdx, endColumnIdx, totalScore)
        }
    }

    @Serializable
    private class TrendSongDto(val songId: String, val title: String, val fullPlayCount: Int = 0, val sectionPlayCount: Int = 0)

    @Serializable
    private class CalendarDto(val items: List<CalendarItemDto> = emptyList())

    @Serializable
    private class CalendarItemDto(val date: String, val practiceSeconds: Long, val practiceCount: Long) {
        fun toModel(): DailyPractice? = epochDayOf(date)?.let { DailyPractice(it, practiceSeconds, practiceCount) }
    }
}

private fun isoDate(epochDay: Long): String = KoreanDateTime.ofEpochDay(epochDay).run {
    "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

/** `YYYY-MM-DD` → 날 수. 형식이 아니면 null. */
private fun epochDayOf(date: String): Long? {
    val parts = date.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return KoreanDateTime(parts[0], parts[1], parts[2], 0, 0).epochDay
}

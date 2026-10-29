package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 연습 기록지 통계 API(GRO-01·02) 요청과 응답 변환. 서버는 MockEngine으로 대신하고, 응답 본문은 BE `GrowthController` 형식이다. */
class HttpGrowthRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(status: HttpStatusCode = HttpStatusCode.OK, body: (path: String) -> String?) = HttpGrowthRepository(
        createHttpClient(
            MockEngine { request ->
                requests += request
                respond(body(request.url.encodedPath) ?: error("연결 실패"), status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            ServerAddress.Production,
        ),
    )

    private val monday = KoreanDateTime(2026, 10, 26, 0, 0).epochDay
    private val sunday = monday + 6

    private fun ok(result: String) = """{"httpStatus":"OK","isSuccess":true,"message":"성공","code":200,"result":$result}"""

    private val summaryJson = ok(
        """
        {"from":"2026-10-26","to":"2026-11-01","practiceCount":3,"practiceSeconds":299,"practiceDays":2,"currentStreakDays":2,
         "averageTotalScore":78,"previousPeriod":{"practiceSeconds":120,"practiceCount":1,"averageTotalScore":null},
         "completedMissionCount":null,"totalMissionCount":null,
         "bestBySong":[{"songId":"2","title":"도라지타령","bestTotalScore":81},{"songId":"1","title":"아리랑","bestTotalScore":79}],
         "metricGrowth":{"songId":"2","title":"도라지타령","bpm":60,"pitch":{"first":70,"last":82},"timing":{"first":65,"last":74},"duration":{"first":null,"last":null}}}
        """.trimIndent(),
    )

    @Test
    fun summarySendsKstDatesAndMapsResponse() = runTest {
        val summary = repository { summaryJson }.summary(monday, sunday)!!

        val request = requests.single()
        assertEquals("/api/users/me/growth/summary", request.url.encodedPath)
        assertEquals("2026-10-26", request.url.parameters["from"])
        assertEquals("2026-11-01", request.url.parameters["to"])
        assertEquals(PeriodTotals(299, 3, 78), summary.current)
        assertEquals(PeriodTotals(120, 1, null), summary.previous)
        assertEquals(2, summary.practiceDays)
        assertEquals(2, summary.currentStreakDays)
        assertEquals(listOf("도라지타령" to 81, "아리랑" to 79), summary.bestBySong.map { it.title to it.bestTotalScore })
    }

    @Test
    fun metricGrowthMapsTimingAndDurationAndDropsItemsWithoutTwoPlays() = runTest {
        val growth = repository { summaryJson }.summary(monday, sunday)!!.metricGrowth!!

        assertEquals("도라지타령", growth.title)
        assertEquals(60, growth.bpm)
        assertEquals(
            listOf(MetricChange(EvaluationMetric.Pitch, 70, 82), MetricChange(EvaluationMetric.Rhythm, 65, 74)),
            growth.changes,
        )
    }

    @Test
    fun noFullSongPlayMeansNoMetricGrowth() = runTest {
        val empty = ok(
            """
            {"from":"2026-10-26","to":"2026-11-01","practiceCount":0,"practiceSeconds":0,"practiceDays":0,"currentStreakDays":0,
             "previousPeriod":{"practiceSeconds":0,"practiceCount":0},"bestBySong":[],
             "metricGrowth":{"songId":null,"title":null,"bpm":null,"pitch":{"first":null,"last":null},"timing":{"first":null,"last":null},"duration":{"first":null,"last":null}}}
            """.trimIndent(),
        )
        val summary = repository { empty }.summary(monday, sunday)!!

        assertNull(summary.metricGrowth)
        assertNull(summary.current.averageTotalScore)
        assertEquals(emptyList(), summary.bestBySong)
    }

    @Test
    fun calendarParsesDates() = runTest {
        val days = repository {
            ok("""{"items":[{"date":"2026-10-26","practiceSeconds":119,"practiceCount":1},{"date":"2026-11-01","practiceSeconds":0,"practiceCount":0}]}""")
        }.calendar(monday, sunday)!!

        assertEquals("/api/users/me/growth/calendar", requests.single().url.encodedPath)
        assertEquals(listOf(DailyPractice(monday, 119, 1), DailyPractice(sunday, 0, 0)), days)
    }

    @Test
    fun failureResponsesAreNull() = runTest {
        val error = """{"httpStatus":"BAD_REQUEST","isSuccess":false,"message":"잘못된 요청","code":400,"result":{"errorCode":"INVALID_REQUEST"}}"""
        assertNull(repository(HttpStatusCode.BadRequest) { error }.summary(monday, sunday))
        assertNull(repository(HttpStatusCode.ServiceUnavailable) { "<html>점검 중</html>" }.calendar(monday, sunday))
        assertNull(repository { "<html>프록시</html>" }.summary(monday, sunday))
        assertNull(repository { null }.calendar(monday, sunday))
    }
}

package com.ssafy.eolssu.data.practice

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

/** 연습 기록지 날짜별 통계 API(GRO-02) 요청과 응답 변환. 서버는 MockEngine으로 대신하고, 응답 본문은 BE `GrowthController` 형식이다. */
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

    @Test
    fun calendarParsesDates() = runTest {
        val days = repository {
            ok("""{"items":[{"date":"2026-10-26","practiceSeconds":119,"practiceCount":1},{"date":"2026-11-01","practiceSeconds":0,"practiceCount":0}]}""")
        }.calendar(monday, sunday)!!

        assertEquals("/api/users/me/growth/calendar", requests.single().url.encodedPath)
        assertEquals(listOf(DailyPractice(monday, 119, 1), DailyPractice(sunday, 0, 0)), days)
    }

    @Test
    fun calendarSendsKstDates() = runTest {
        repository { ok("""{"items":[]}""") }.calendar(monday - 365, monday)

        val url = requests.single().url
        assertEquals("2025-10-26", url.parameters["from"])
        assertEquals("2026-10-26", url.parameters["to"])
    }

    @Test
    fun failureResponsesAreNull() = runTest {
        val error = """{"httpStatus":"BAD_REQUEST","isSuccess":false,"message":"잘못된 요청","code":400,"result":{"errorCode":"INVALID_REQUEST"}}"""
        assertNull(repository(HttpStatusCode.BadRequest) { error }.calendar(monday, sunday))
        assertNull(repository(HttpStatusCode.ServiceUnavailable) { "<html>점검 중</html>" }.calendar(monday, sunday))
        assertNull(repository { "<html>프록시</html>" }.calendar(monday, sunday))
        assertNull(repository { null }.calendar(monday, sunday))
    }
}

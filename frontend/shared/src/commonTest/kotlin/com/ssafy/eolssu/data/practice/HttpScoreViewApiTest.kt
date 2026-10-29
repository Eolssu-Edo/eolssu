package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 체류 기록 API(GRO-04) 요청 형식. 서버는 MockEngine으로 대신한다. */
class HttpScoreViewApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(status: (path: String) -> HttpStatusCode?) = HttpScoreViewApi(
        createHttpClient(
            MockEngine { request ->
                requests += request
                respond("", status(request.url.encodedPath) ?: error("연결 실패"))
            },
            ServerAddress.Production,
        ),
    )

    private val segment = PracticeTimeSegment(
        key = "8f1c2d3e-0000-4000-8000-000000000001",
        songId = "12",
        startedAtMillis = 1_793_163_600_000, // 2026-10-28T05:00:00Z
        endedAtMillis = 1_793_163_900_500,
        visibleSeconds = 300,
        countedSeconds = 290,
    )

    @Test
    fun sendsKeyHeaderAndUtcBody() = runTest {
        assertEquals(201, api { HttpStatusCode.Created }.post(segment))

        val request = requests.single()
        assertEquals("/api/users/me/score-views", request.url.encodedPath)
        assertEquals(segment.key, request.headers["Idempotency-Key"])
        assertEquals(
            """{"songId":"12","startedAt":"2026-10-28T05:00:00Z","endedAt":"2026-10-28T05:05:00.500Z","visibleSeconds":300,"countedSeconds":290}""",
            (request.body as TextContent).text,
        )
    }

    @Test
    fun resendingSameSegmentSendsSameBody() = runTest {
        val api = api { HttpStatusCode.OK }
        api.post(segment)
        api.post(segment)
        assertEquals((requests[0].body as TextContent).text, (requests[1].body as TextContent).text)
    }

    @Test
    fun unauthorizedIsRetriedByClientBeforeQueueSeesIt() = runTest {
        var refreshed = false
        val api = api { path ->
            when {
                path == "/api/auth/refresh" -> HttpStatusCode.OK.also { refreshed = true }
                refreshed -> HttpStatusCode.Created
                else -> HttpStatusCode.Unauthorized
            }
        }
        assertEquals(201, api.post(segment))
    }

    @Test
    fun noResponseIsNull() = runTest {
        assertNull(api { null }.post(segment))
    }
}

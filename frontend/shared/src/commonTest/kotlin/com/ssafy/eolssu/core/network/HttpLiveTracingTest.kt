package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.diagnostics.EvaluationTiming
import com.ssafy.eolssu.core.diagnostics.PerfKind
import com.ssafy.eolssu.core.diagnostics.PerformanceEvent
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpLiveTracingTest {
    @Test
    fun startIsVisibleBeforeSendAndResponseSharesOnlySafeIdentifiers() = runTest {
        val events = mutableListOf<PerformanceEvent>()
        var requestId: String? = null
        val client = createHttpClient(MockEngine { request ->
            assertEquals(PerfKind.REQUEST_STARTED, events.single().kind)
            requestId = request.headers["X-QA-Request-Id"]
            assertEquals(events.single().requestId, requestId)
            respond("", HttpStatusCode.OK)
        }, ServerAddress("http://127.0.0.1:18180"))
        val timing = EvaluationTiming()
        PerformanceLog.liveRequestsEnabled = true
        PerformanceLog.sink = { events += it }
        try {
            withContext(timing) {
                client.post("/api/practice-sessions/1/recording?email=private@example.test") {
                    headers.append(EvaluationTiming.HEADER, timing.id)
                    headers.append("Cookie", "private-cookie")
                    setBody("private-password")
                }
            }
            assertEquals(listOf(PerfKind.REQUEST_STARTED, PerfKind.REQUEST_FINISHED), events.map { it.kind })
            assertNotNull(requestId)
            assertEquals(2, events.count { it.requestId == requestId })
            assertTrue(events.all { it.evaluationTimingId == timing.id })
            assertEquals(200, events.last().status)
            assertTrue(events.all { !it.encode().contains("private") && !it.encode().contains("/api/") })
        } finally {
            PerformanceLog.liveRequestsEnabled = false
            PerformanceLog.sink = null
            client.close()
        }
    }

    @Test
    fun refreshAndReplayAreDistinctActualRequests() = runTest {
        val events = mutableListOf<PerformanceEvent>()
        val headerIds = mutableListOf<String?>()
        var refreshed = false
        val client = createHttpClient(MockEngine { request ->
            headerIds += request.headers["X-QA-Request-Id"]
            when {
                request.url.encodedPath == "/api/auth/refresh" ->
                    respond("", HttpStatusCode.OK).also { refreshed = true }
                refreshed -> respond("", HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }, ServerAddress("http://127.0.0.1:18180"))
        PerformanceLog.liveRequestsEnabled = true
        PerformanceLog.sink = { events += it }
        try {
            client.get("/api/users/me")
            assertEquals(3, headerIds.filterNotNull().distinct().size)
            assertEquals(listOf(401, 200, 200),
                events.filter { it.kind == PerfKind.REQUEST_FINISHED }.map { it.status })
            assertEquals(headerIds,
                events.filter { it.kind == PerfKind.REQUEST_STARTED }.map { it.requestId })
        } finally {
            PerformanceLog.liveRequestsEnabled = false
            PerformanceLog.sink = null
            client.close()
        }
    }

    @Test
    fun failedSendTerminatesTheSameRequest() = runTest {
        val events = mutableListOf<PerformanceEvent>()
        val client = createHttpClient(MockEngine { throw ConnectTimeoutException("private-host") },
            ServerAddress("http://127.0.0.1:18180"))
        PerformanceLog.liveRequestsEnabled = true
        PerformanceLog.sink = { events += it }
        try {
            assertFailsWith<ConnectTimeoutException> { client.get("/api/songs/1") }
            assertEquals(listOf(PerfKind.REQUEST_STARTED, PerfKind.REQUEST_FINISHED), events.map { it.kind })
            assertEquals(events.first().requestId, events.last().requestId)
            assertEquals("http_timeout", events.last().outcome?.name?.lowercase())
            assertTrue(events.none { "private-host" in it.encode() })
        } finally {
            PerformanceLog.liveRequestsEnabled = false
            PerformanceLog.sink = null
            client.close()
        }
    }

    @Test
    fun cancelledSendRetainsItsCancellationAndTerminatesTrace() = runTest {
        val events = mutableListOf<PerformanceEvent>()
        val client = createHttpClient(MockEngine { throw CancellationException("cancelled") },
            ServerAddress("http://127.0.0.1:18180"))
        PerformanceLog.liveRequestsEnabled = true
        PerformanceLog.sink = { events += it }
        try {
            assertFailsWith<CancellationException> { client.get("/api/songs/1") }
            assertEquals(listOf(PerfKind.REQUEST_STARTED, PerfKind.REQUEST_FINISHED), events.map { it.kind })
            assertEquals(events.first().requestId, events.last().requestId)
            assertEquals("cancelled", events.last().outcome?.name?.lowercase())
        } finally {
            PerformanceLog.liveRequestsEnabled = false
            PerformanceLog.sink = null
            client.close()
        }
    }

    @Test
    fun inactiveOrProductionClientSendsNoDiagnosticHeader() = runTest {
        for ((enabled, server) in listOf(false to ServerAddress("http://127.0.0.1:18180"),
                true to ServerAddress.Production)) {
            val events = mutableListOf<PerformanceEvent>()
            val client = createHttpClient(MockEngine { request ->
                assertEquals(null, request.headers["X-QA-Request-Id"])
                respond("", HttpStatusCode.OK)
            }, server)
            PerformanceLog.liveRequestsEnabled = enabled
            PerformanceLog.sink = { events += it }
            try {
                client.get("/api/songs/1")
                assertTrue(events.isEmpty())
            } finally {
                PerformanceLog.liveRequestsEnabled = false
                PerformanceLog.sink = null
                client.close()
            }
        }
    }
}

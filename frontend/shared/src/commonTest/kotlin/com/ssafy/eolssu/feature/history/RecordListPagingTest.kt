package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.HttpPracticeRecordRepository
import com.ssafy.eolssu.testing.CatalogViewModelTest
import com.ssafy.eolssu.testing.CatalogSongs
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordListPagingTest : CatalogViewModelTest() {
    private data class Call(val song: String?, val page: Int, val sort: String, val size: Int)

    @Test
    fun onlyOpenGroupsRequestPagesAndContinuationCanRetry() = runTest {
        val calls = mutableListOf<Call>()
        var failNext = false
        val date = KoreanDateTime(2026, 11, 11, 15, 0)
        val store = FakeKeyValueStore()
        val member = SignedInMember(store)
        member.set("fixture-a")
        val client = createHttpClient(MockEngine { request ->
            val params = request.url.parameters
            val call = Call(params["songId"], params["page"]!!.toInt(), params["sort"]!!, params["size"]!!.toInt())
            calls += call
            if (failNext) {
                failNext = false
                respond("{}", HttpStatusCode.ServiceUnavailable, JSON)
            } else {
                val song = call.song ?: "1"
                val all = if (song == "1") (125 downTo 101).toList() else listOf((song.toInt() * 100) + 1)
                val ids = (if (call.sort == "OLDEST") all.reversed() else all).drop(call.page * call.size).take(call.size)
                val items = ids.joinToString(",") { id ->
                    """{"sessionId":"$id","songId":"$song","title":"곡 $song","bpm":60,"totalScore":80,"startedAt":"2026-11-11T06:${(id % 100).toString().padStart(2, '0')}:00Z"}"""
                }
                val week = if (params["weekOf"] != null) ""","week":{"recordCount":29,"bestScore":94}""" else ""
                respond("""{"isSuccess":true,"code":200,"result":{"totalRecordCount":29,"songs":$songsJson,
                    "songId":"$song","items":[$items],"totalPages":${(all.size + call.size - 1) / call.size}$week}}""",
                    HttpStatusCode.OK, JSON)
            }
        }, ServerAddress.Production)
        val repo = HttpPracticeRecordRepository(client, NoEvaluation, member)
        val catalog = CatalogSongs()
        try {
            val before = TimeSource.Monotonic.markNow()
            assertTrue(repo.refresh())
            val beforeLoad = before.elapsedNow()
            val beforeCalls = calls.toList()
            assertEquals(5, beforeCalls.size)
            val beforeOpen = TimeSource.Monotonic.markNow()
            assertEquals("201", repo.records.value.first { it.songId == "2" }.id)
            val beforeOpenLoad = beforeOpen.elapsedNow()

            calls.clear()
            val after = TimeSource.Monotonic.markNow()
            val model = keep(RecordListViewModel(null, repo, now = { 1794376800000 }, member = member, songs = catalog))
            model.uiState.first { it.pages["1"]?.records?.size == 20 }
            val afterLoad = after.elapsedNow()
            assertEquals(listOf(Call(null, 0, "NEWEST", 20)), calls)
            assertEquals(listOf("1", "2", "3", "4", "5"), model.uiState.value.groups.map { it.songId })
            assertEquals(29 to 94, model.uiState.value.thisWeek)
            assertEquals(20, model.uiState.value.pages.getValue("1").records.size)
            assertEquals(0, catalog.listCalls)
            assertEquals(0, catalog.songCalls)

            val afterOpen = TimeSource.Monotonic.markNow()
            model.toggle("2")
            model.uiState.first { it.pages["2"]?.records?.isNotEmpty() == true }
            val afterOpenLoad = afterOpen.elapsedNow()
            assertEquals(Call("2", 0, "NEWEST", 20), calls.last())
            assertEquals("201", model.uiState.value.pages.getValue("2").records.single().id)
            println("REC02 fixture: list ${beforeCalls.size} requests / $beforeLoad -> 1 request / $afterLoad, " +
                "expand 0 requests / $beforeOpenLoad -> 1 request / $afterOpenLoad")

            model.toggle("2")
            model.toggle("2")
            assertEquals(2, calls.size)

            failNext = true
            model.loadMore("1")
            model.uiState.first { it.pages["1"]?.failed == true }
            assertEquals(20, model.uiState.value.pages.getValue("1").records.size)
            model.loadMore("1")
            model.uiState.first { it.pages["1"]?.records?.size == 25 }
            assertEquals(listOf(1, 1), calls.takeLast(2).map { it.page })
            assertEquals((125 downTo 101).map(Int::toString), model.uiState.value.pages.getValue("1").records.map { it.id })

            model.changeSort(RecordSort.Oldest)
            model.uiState.first { it.pages["1"]?.records?.isNotEmpty() == true && it.pages["2"]?.records?.isNotEmpty() == true }
            assertEquals(setOf("1", "2"), calls.takeLast(2).map { it.song }.toSet())
            assertTrue(calls.takeLast(2).all { it.sort == "OLDEST" && it.page == 0 })
            assertEquals("101", model.uiState.value.pages.getValue("1").records.first().id)

            model.changeInstrument(Instrument.DaegeumSanjo)
            assertTrue(model.uiState.value.groups.isEmpty())
            model.changeInstrument(Instrument.Sogeum)
            assertEquals(listOf("2"), model.uiState.value.groups.map { it.songId })
            assertTrue(model.uiState.value.isOpen("2"))
            model.show("401")
            assertEquals("401", model.uiState.value.selectedRecordId)

            member.set("fixture-b")
            runCurrent()
            assertTrue(model.uiState.value.summaries.isEmpty())
            assertTrue(model.uiState.value.pages.isEmpty())
            assertTrue(model.uiState.value.songInstruments.isEmpty())
            assertFalse(model.uiState.value.showLoadStatus)
            assertEquals(null, model.uiState.value.selectedRecordId)
        } finally {
            client.close()
        }
    }

    private val songsJson = (1..5).joinToString(",", "[", "]") { id ->
        val code = if (id == 2) "SOGEUM" else "DANSO"
        """{"songId":"$id","title":"곡 $id","recordCount":${if (id == 1) 25 else 1},
            "lastSavedAt":"2026-11-11T06:20:00Z","lastStartedAt":"2026-11-11T06:20:00Z","bestScore":94,"instrumentCode":"$code"}"""
    }

    private object NoEvaluation : EvaluationRepository {
        override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int) = error("쓰지 않음")
        override suspend fun analysisStatus(analysisId: String) = error("쓰지 않음")
        override suspend fun retryAnalysis(analysisId: String) = error("쓰지 않음")
        override suspend fun result(evaluationId: String): EvaluationResult? = null
    }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
    }
}

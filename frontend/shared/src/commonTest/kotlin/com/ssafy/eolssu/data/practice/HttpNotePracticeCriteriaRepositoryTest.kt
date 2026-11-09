package com.ssafy.eolssu.data.practice

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

/** 한 음씩 연습 판정 기준 조회(PRA-06). 서버는 MockEngine으로 대신한다. */
class HttpNotePracticeCriteriaRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var reply: () -> Pair<HttpStatusCode, String>? = { null }

    private val repository = HttpNotePracticeCriteriaRepository(
        createHttpClient(
            MockEngine { request ->
                requests += request
                val (status, body) = reply() ?: error("연결 실패")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
            ServerAddress.Production,
        ),
    )

    private fun ok(tuning: String = "FIRST_NOTE", sigimsae: String = "MAIN_PITCH", confidence: String = "0.8") = HttpStatusCode.OK to
        """{"httpStatus":"OK","isSuccess":true,"message":"한 음씩 연습 판정 기준 조회 성공","code":200,"result":
        {"instrumentId":"3","criteriaVersion":"note-step-1","pitchToleranceCent":50,"holdMs":300,"minConfidence":$confidence,
         "minLevelDbfs":-45,"octaveMustMatch":true,"tuning":"$tuning","maxTuningOffsetCent":50,"sigimsaeMode":"$sigimsae","hintAfterMs":8000}}"""

    @Test fun readsCriteriaForTheInstrument() = runTest {
        reply = { ok() }
        assertEquals(
            NotePracticeCriteriaLoad.Found(NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, true, true, 50.0, false, 8000)),
            repository.criteria("3"),
        )
        assertEquals("/api/note-practice/criteria", requests.single().url.encodedPath)
        assertEquals("3", requests.single().url.parameters["instrumentId"])

        reply = { ok(tuning = "NONE", sigimsae = "SKIP") }
        val other = repository.criteria("3") as NotePracticeCriteriaLoad.Found
        assertEquals(false, other.criteria.firstNoteTuning)
        assertEquals(true, other.criteria.skipSigimsae)
    }

    @Test fun missingCriteriaHideTheModeAndOtherFailuresDoNot() = runTest {
        reply = { HttpStatusCode.NotFound to """{"httpStatus":"NOT_FOUND","isSuccess":false,"code":404,"result":{"errorCode":"RESOURCE_NOT_FOUND"}}""" }
        assertEquals(NotePracticeCriteriaLoad.Unsupported, repository.criteria("9"))

        reply = { HttpStatusCode.InternalServerError to """{"httpStatus":"INTERNAL_SERVER_ERROR","isSuccess":false,"code":500}""" }
        assertEquals(NotePracticeCriteriaLoad.Failed, repository.criteria("3"))

        reply = { null }
        assertEquals(NotePracticeCriteriaLoad.Failed, repository.criteria("3"))

        reply = { ok(tuning = "LATER") }
        assertEquals(NotePracticeCriteriaLoad.Failed, repository.criteria("3"))

        reply = { ok(confidence = "1.5") }
        assertEquals(NotePracticeCriteriaLoad.Failed, repository.criteria("3"))
    }
}

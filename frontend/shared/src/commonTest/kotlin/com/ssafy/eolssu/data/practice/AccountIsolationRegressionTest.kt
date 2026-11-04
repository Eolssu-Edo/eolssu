package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 같은 입력과 순서로 계정 변경 전후의 잘못된 발송·응답·캐시 건수를 비교한다. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountIsolationRegressionTest {
    private val member = SignedInMember(FakeKeyValueStore()).apply { set("A") }

    @Test
    fun queuedCreateDoesNotSendUnderAnotherAccount() = queuedCreate("B")

    @Test
    fun queuedCreateDoesNotSurviveSameAccountRelogin() = queuedCreate("A")

    private fun queuedCreate(next: String) = runTest {
        var posts = 0
        val client = createHttpClient(MockEngine {
            posts++
            respond("""{"isSuccess":true,"result":{"sessionId":"41","startedAt":"2026-11-04T00:00:00Z"}}""",
                HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val changing = async {
            member.changing {
                entered.complete(Unit)
                release.await()
                member.set(null)
                member.set(next)
            }
        }
        entered.await()
        val request = async { runCatching { HttpPracticeSessionRepository(client, member).create("2", 60, "take-A") } }
        runCurrent()
        release.complete(Unit)
        changing.await()
        request.await()
        println("ACCOUNT_METRIC queued_create_$next=$posts denominator=1")
        client.close()
        assertEquals(0, posts)
    }

    @Test
    fun switchingAccountRemovesLoadedRecords() = runTest {
        val client = createHttpClient(MockEngine { respond(records, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }, ServerAddress.Production)
        val repository = HttpPracticeRecordRepository(client, evaluations, member)
        repository.refresh()
        assertEquals(1, repository.records.value.size)
        member.changing { member.set("B") }
        val remaining = repository.records.value.size
        println("ACCOUNT_METRIC cached_records=$remaining denominator=1")
        client.close()
        assertEquals(0, remaining)
    }

    @Test
    fun previousLoginResponseCannotPopulateNewLogin() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = createHttpClient(MockEngine {
            entered.complete(Unit)
            release.await()
            respond(records, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val repository = HttpPracticeRecordRepository(client, evaluations, member)
        val request = async { runCatching { repository.refresh() } }
        entered.await()
        member.set("B")
        member.set("A")
        release.complete(Unit)
        request.await()
        val applied = repository.records.value.size
        println("ACCOUNT_METRIC stale_records=$applied denominator=1")
        client.close()
        assertEquals(0, applied)
    }


    @Test
    fun accountChangeRunsBetweenPagesAndStopsTheNextRequest() = runTest {
        var gets = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = createHttpClient(MockEngine {
            gets++
            if (gets == 1) { entered.complete(Unit); release.await() }
            respond(records.replace("\"totalPages\":1", "\"totalPages\":2"), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val repository = HttpPracticeRecordRepository(client, evaluations, member)
        val loading = async { runCatching { repository.refresh() } }
        entered.await()
        val changing = async { member.changing { member.set("B") } }
        runCurrent()
        release.complete(Unit)
        changing.await()
        loading.await()
        assertEquals(1, gets, "인증 변경은 전체 페이지가 끝날 때까지 기다리지 않는다")
        assertEquals(0, repository.records.value.size)
        client.close()
    }

    @Test
    fun queuedMemoAndDeleteCannotUseTheNewLogin() = runTest {
        var writes = 0
        val client = createHttpClient(MockEngine {
            writes++
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val repository = HttpPracticeRecordRepository(client, evaluations, member)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val changing = async { member.changing { entered.complete(Unit); release.await(); member.set("B") } }
        entered.await()
        val memo = async { runCatching { repository.updateMemo("41", "A의 메모") } }
        val delete = async { runCatching { repository.delete("41") } }
        runCurrent()
        release.complete(Unit)
        changing.await(); memo.await(); delete.await()
        assertEquals(0, writes)
        client.close()
    }


    @Test
    fun queuedWithdrawalCannotDeleteAnotherLogin() = runTest {
        var deletes = 0
        val client = createHttpClient(MockEngine {
            deletes++
            respond("{}", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        val users = com.ssafy.eolssu.data.user.ApiUserRepository(client, null, member = member)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val changing = async { member.changing { entered.complete(Unit); release.await(); member.set("B") } }
        entered.await()
        val withdrawal = async { runCatching { users.withdraw("A-password") } }
        runCurrent()
        release.complete(Unit)
        changing.await(); withdrawal.await()
        assertEquals(0, deletes)
        client.close()
    }

    private val records = """{"isSuccess":true,"result":{"songs":[{"songId":"2"}],"songId":"2","items":[{"sessionId":"41","songId":"2","title":"fixture","bpm":60,"totalScore":80,"startedAt":"2026-11-04T00:00:00Z"}],"totalPages":1}}"""
    private val evaluations = object : EvaluationRepository {
        override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String = error("unused")
        override suspend fun analysisStatus(analysisId: String): AnalysisStatus = error("unused")
        override suspend fun retryAnalysis(analysisId: String): RetryOutcome = error("unused")
        override suspend fun result(evaluationId: String) = null
    }
}

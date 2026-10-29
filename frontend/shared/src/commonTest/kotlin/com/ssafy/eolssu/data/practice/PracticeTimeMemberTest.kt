package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.model.PracticeTimeSnapshot
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.ApiAuthRepository
import com.ssafy.eolssu.data.user.SampleAuthRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 연습 시간 기록은 측정한 회원으로만 보낸다(`docs/specs/practice-time.md`의 '기기 저장과 전송' 6).
 * 로그아웃을 거치지 않고 회원이 바뀌는 경로(세션 만료, 탈퇴, 다른 회원 로그인, 앱 재실행)를 본다.
 * 서버(로그인·토큰 갱신·체류 기록)는 MockEngine으로 대신하고, 이메일이 `a@`면 회원 A, `b@`면 회원 B로 로그인된다.
 */
class PracticeTimeMemberTest {

    private val store = FakeKeyValueStore()

    /** 서버가 받은 체류 기록의 구간 키(`Idempotency-Key`). */
    private val sent = mutableListOf<String>()
    private val requests = mutableListOf<String>()
    private var scoreViewStatus = HttpStatusCode.Created
    private var refreshStatus = HttpStatusCode.OK

    private val client = createHttpClient(
        MockEngine { request ->
            val path = request.url.encodedPath
            requests += path
            val (status, body) = when (path) {
                "/api/auth/sign-in" -> {
                    val userId = if ((request.body as TextContent).text.contains("\"a@")) "A" else "B"
                    HttpStatusCode.OK to """{"isSuccess":true,"result":{"userId":"$userId","withdrawalCancelled":false}}"""
                }
                "/api/auth/refresh" -> refreshStatus to "{}"
                "/api/users/me/score-views" -> {
                    if (scoreViewStatus == HttpStatusCode.Created) sent += request.headers["Idempotency-Key"].orEmpty()
                    scoreViewStatus to "{}"
                }
                else -> error(path)
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
        ServerAddress.Production,
    )

    /**
     * 앱 한 번의 실행. 같은 [store]로 새로 만들면 앱을 다시 켠 것과 같다.
     * 기록은 API 없는 [recorder]로 남기고, 전송은 [queue]의 `send()`를 기다려 확인한다(배경 전송과 섞이지 않게).
     */
    private inner class App(scope: CoroutineScope) {
        val member = SignedInMember(store)
        val auth = ApiAuthRepository(client, null, member, scope, SampleAuthRepository { 0L })
        val recorder = PracticeTimeQueue(store, null, member, scope)
        val queue = PracticeTimeQueue(store, HttpScoreViewApi(client), member, scope)
    }

    @Test
    fun expiredSessionKeepsRecordsButAnotherMemberNeverSendsThem() = runTest {
        val app = App(this)
        app.auth.logIn("a@example.com", PASSWORD)
        app.recorder.close(segment("a"))

        // 회원 A의 세션 만료: 토큰 갱신도 401
        scoreViewStatus = HttpStatusCode.Unauthorized
        refreshStatus = HttpStatusCode.Unauthorized
        app.queue.send()
        assertEquals(listOf(segment("a")), app.queue.pending)

        // 로그아웃 없이 회원 B로 로그인
        scoreViewStatus = HttpStatusCode.Created
        refreshStatus = HttpStatusCode.OK
        app.auth.logIn("b@example.com", PASSWORD)
        requests.clear()
        app.queue.send()

        assertEquals(emptyList(), requests) // 회원 B의 인증으로 보내지 않는다
        assertEquals(emptyList(), app.queue.pending)
    }

    @Test
    fun expiredSessionRecordsAreSentWhenSameMemberSignsInAgain() = runTest {
        val app = App(this)
        app.auth.logIn("a@example.com", PASSWORD)
        app.recorder.close(segment("a"))
        scoreViewStatus = HttpStatusCode.Unauthorized
        refreshStatus = HttpStatusCode.Unauthorized
        app.queue.send()

        scoreViewStatus = HttpStatusCode.Created
        refreshStatus = HttpStatusCode.OK
        app.auth.logIn("a@example.com", PASSWORD)
        app.queue.send()

        assertEquals(listOf("a"), sent)
        assertEquals(emptyList(), app.queue.pending)
    }

    @Test
    fun withdrawalDropsRecordsSoNextMemberCannotSendThem() = runTest {
        val app = App(this)
        app.auth.logIn("a@example.com", PASSWORD)
        app.recorder.close(segment("a"))
        app.recorder.saveOpen(snapshot("b"))

        app.recorder.discard() // 탈퇴 신청(WithdrawViewModel)
        testScheduler.advanceUntilIdle()
        app.auth.logIn("b@example.com", PASSWORD)
        app.recorder.onAppStart()
        app.queue.send()

        assertEquals(emptyList(), sent)
        assertEquals(emptyList(), app.queue.pending)
    }

    @Test
    fun relaunchKeepsSignedInMemberAndSendsLeftoverOpenSegment() = runTest {
        App(this).run {
            auth.logIn("a@example.com", PASSWORD)
            recorder.saveOpen(snapshot("a"))
        }
        // 다음 저장 전에 앱이 갑자기 종료되고 다시 켜짐. 로그인 쿠키와 회원은 남아 있다.
        val relaunched = App(this)
        relaunched.recorder.onAppStart()
        relaunched.queue.send()

        assertEquals(listOf("a"), sent)
    }

    @Test
    fun relaunchThenAnotherMemberSignInDropsLeftoverOpenSegment() = runTest {
        App(this).run {
            auth.logIn("a@example.com", PASSWORD)
            recorder.close(segment("a"))
            recorder.saveOpen(snapshot("b"))
        }
        val relaunched = App(this)
        relaunched.auth.logIn("b@example.com", PASSWORD)
        relaunched.recorder.onAppStart()
        relaunched.queue.send()

        assertEquals(emptyList(), sent)
        assertEquals(emptyList(), relaunched.queue.pending)
    }

    private fun segment(key: String) = PracticeTimeSegment(
        key = key, songId = "12", startedAtMillis = START, endedAtMillis = START + 60_000, visibleSeconds = 60, countedSeconds = 60,
    )

    private fun snapshot(key: String) = PracticeTimeSnapshot(
        key = key, songId = "12", startedAtMillis = START, visibleMillis = 60_000, countedMillis = 60_000, savedAtMillis = START + 60_000,
    )

    private companion object {
        const val START = 1_793_000_000_000L
        const val PASSWORD = "eolssu1!"
    }
}

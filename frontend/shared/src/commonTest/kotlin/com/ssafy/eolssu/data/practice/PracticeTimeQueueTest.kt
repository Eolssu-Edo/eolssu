package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.PracticeTimeSegment
import com.ssafy.eolssu.core.model.PracticeTimeSnapshot
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.testing.runImmediately
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 연습 시간 기록의 기기 저장과 전송(`docs/specs/practice-time.md`의 '기기 저장과 전송'). 저장소·HTTP는 대역을 쓴다. */
class PracticeTimeQueueTest {

    private val store = FakeKeyValueStore()
    private val api = FakeScoreViewApi()
    private val errors = mutableListOf<String>()

    /** 로그인한 회원도 같은 [store]에 있다. 처음에는 회원 A로 로그인한 상태다. */
    private val member = SignedInMember(store).apply { set("A") }

    /** 같은 [store]로 새로 만들면 앱을 다시 켠 것과 같다. 전송은 멈추지 않으므로 그 자리에서 끝난다. */
    private fun queue(api: ScoreViewApi? = this.api) =
        PracticeTimeQueue(store, api, SignedInMember(store), CoroutineScope(Dispatchers.Unconfined), logError = { errors += it })

    @Test
    fun closedSegmentIsSentRightAway() {
        val queue = queue()
        queue.saveOpen(snapshot("a"))
        queue.close(segment("a"))

        assertEquals(listOf(segment("a")), api.posted)
        assertEquals(emptyList(), queue.pending)
        queue.onAppStart()
        assertEquals(1, api.posted.size) // 열린 구간도 지워져 다시 닫지 않는다
    }

    @Test
    fun shortSegmentOnlyClearsOpenState() {
        val queue = queue()
        queue.saveOpen(snapshot("a"))
        queue.close(null)
        queue.onAppStart()
        assertEquals(emptyList(), api.posted)
    }

    @Test
    fun withoutApiSegmentsStayOnDeviceAcrossRelaunch() {
        queue(api = null).close(segment("a"))
        queue(api = null).close(segment("b"))
        assertEquals(listOf(segment("a"), segment("b")), queue(api = null).pending)
    }

    @Test
    fun savedAndRejectedResponsesRemoveSegment() {
        for (status in listOf(201, 200, 409, 400, 404, 403)) {
            api.statuses += status
            queue().close(segment("s$status"))
        }
        assertEquals(emptyList(), queue().pending)
        assertEquals(4, errors.size) // 409·400·404·403은 오류로 남긴다
    }

    @Test
    fun serverAndNetworkErrorsKeepSegmentAndStopSending() {
        val queue = queue(api = null)
        queue.close(segment("a"))
        queue.close(segment("b"))

        api.statuses += listOf(503, null)
        runImmediately { queue().send() }
        runImmediately { queue().send() }
        assertEquals(listOf(segment("a"), segment("a")), api.posted) // 실패하면 뒤의 구간은 보내지 않는다
        assertEquals(listOf(segment("a"), segment("b")), queue().pending)

        runImmediately { queue().send() }
        assertEquals(emptyList(), queue().pending)
        assertEquals(segment("a"), api.posted[2]) // 같은 구간은 같은 키·같은 값으로 다시 보낸다
    }

    @Test
    fun unauthorizedAfterFailedRefreshKeepsSegment() {
        api.statuses += 401
        queue().close(segment("a"))
        assertEquals(listOf(segment("a")), queue().pending)
        assertEquals(emptyList(), errors)
    }

    @Test
    fun leftoverOpenSegmentIsClosedAtLastSaveOnNextStart() {
        queue(api = null).saveOpen(snapshot("a", visibleMillis = 270_000, savedAtMillis = START + 270_000))
        // 다음 저장 전에 앱이 갑자기 종료되고 다시 켜짐
        queue().onAppStart()

        val sent = api.posted.single()
        assertEquals("a", sent.key)
        assertEquals(START + 270_000, sent.endedAtMillis)
        assertEquals(270, sent.visibleSeconds)
        queue().onAppStart()
        assertEquals(1, api.posted.size)
    }

    @Test
    fun leftoverUnderTenSecondsIsDropped() {
        queue(api = null).saveOpen(snapshot("a", visibleMillis = 9_000, savedAtMillis = START + 9_000))
        queue().onAppStart()
        assertEquals(emptyList(), api.posted)
    }

    @Test
    fun leftoverAlreadyQueuedIsNotAddedTwice() {
        val offline = queue(api = null)
        offline.saveOpen(snapshot("a"))
        offline.close(segment("a"))
        // 대기열에 넣은 뒤 열린 상태를 지우기 전에 종료된 경우
        store.values["practice_time.open"] = encodedOpen("a")

        queue(api = null).onAppStart()
        assertEquals(listOf(segment("a")), queue(api = null).pending)
    }

    @Test
    fun logOutTriesOnceThenClearsEverything() {
        val offline = queue(api = null)
        offline.close(segment("a"))
        offline.saveOpen(snapshot("b"))
        api.statuses += listOf(201, 503)

        var loggedOut = false
        queue().logOut { loggedOut = true }

        assertEquals(listOf("a", "b"), api.posted.map { it.key })
        assertEquals(emptyList(), queue().pending)
        queue().onAppStart()
        assertEquals(2, api.posted.size)
        assertTrue(loggedOut)
    }

    @Test
    fun otherMembersRecordsAreDroppedWithoutSending() {
        val offline = queue(api = null)
        offline.close(segment("a"))
        offline.saveOpen(snapshot("b"))
        // 로그아웃 없이 회원 B로 로그인(세션 만료 뒤 다른 회원 로그인)한 뒤 앱이 앞으로 옴
        member.set("B")
        queue().onAppStart()

        assertEquals(emptyList(), api.posted)
        assertEquals(emptyList(), queue().pending)
        assertEquals(2, errors.size)
    }

    @Test
    fun onlyCurrentMembersRecordsAreSent() {
        queue(api = null).close(segment("a"))
        member.set("B")
        queue(api = null).close(segment("b"))
        queue().onAppStart()

        assertEquals(listOf("b"), api.posted.map { it.key })
        assertEquals(emptyList(), queue().pending)
    }

    @Test
    fun unknownMemberKeepsRecordsUntilSameMemberSignsInAgain() {
        queue(api = null).close(segment("a"))
        member.set(null)
        queue().onAppStart()
        assertEquals(emptyList(), api.posted)
        assertEquals(listOf(segment("a")), queue().pending)

        member.set("A")
        queue().onAppStart()
        assertEquals(listOf(segment("a")), api.posted)
    }

    @Test
    fun recordsMeasuredWithoutMemberAreNeverSent() {
        member.set(null)
        queue(api = null).close(segment("a"))
        member.set("A")
        queue().onAppStart()

        assertEquals(emptyList(), api.posted)
        assertEquals(emptyList(), queue().pending)
    }

    @Test
    fun discardClearsEverythingWithoutSending() {
        val offline = queue(api = null)
        offline.close(segment("a"))
        offline.saveOpen(snapshot("b"))

        queue().discard()
        queue().onAppStart()

        assertEquals(emptyList(), api.posted)
        assertEquals(emptyList(), queue().pending)
    }

    @Test
    fun separatorsInValuesSurviveStorage() {
        val odd = segment("a").copy(songId = "곡\t1\n%09")
        queue(api = null).close(odd)
        assertEquals(listOf(odd), queue(api = null).pending)
    }

    @Test
    fun brokenLinesAreSkipped() {
        queue(api = null).close(segment("a"))
        // 숫자가 아닌 칸, 회원 칸이 없는 이전 형식
        store.values["practice_time.queue"] = store.values.getValue("practice_time.queue") +
            "\n깨진 줄\nA\tb\t12\tx\t0\t0\t0\nc\t12\t0\t0\t0\t0"
        assertEquals(listOf(segment("a")), queue(api = null).pending)
    }

    private fun encodedOpen(key: String): String {
        val probe = FakeKeyValueStore()
        PracticeTimeQueue(probe, null, SignedInMember(probe), CoroutineScope(Dispatchers.Unconfined)).saveOpen(snapshot(key))
        return probe.values.getValue("practice_time.open")
    }

    private fun segment(key: String) = PracticeTimeSegment(
        key = key, songId = "12", startedAtMillis = START, endedAtMillis = START + 60_000, visibleSeconds = 60, countedSeconds = 60,
    )

    private fun snapshot(key: String, visibleMillis: Long = 60_000, savedAtMillis: Long = START + 60_000) = PracticeTimeSnapshot(
        key = key, songId = "12", startedAtMillis = START, visibleMillis = visibleMillis, countedMillis = visibleMillis,
        savedAtMillis = savedAtMillis,
    )

    private class FakeScoreViewApi : ScoreViewApi {
        /** 차례로 돌려줄 응답 코드(null은 응답 없음). 다 쓰면 201. */
        val statuses = mutableListOf<Int?>()
        val posted = mutableListOf<PracticeTimeSegment>()

        override suspend fun post(segment: PracticeTimeSegment): Int? {
            posted += segment
            return if (statuses.isEmpty()) 201 else statuses.removeAt(0)
        }
    }

    private companion object {
        const val START = 1_793_000_000_000L
    }
}

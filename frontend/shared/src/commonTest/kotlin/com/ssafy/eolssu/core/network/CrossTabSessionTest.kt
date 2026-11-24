package com.ssafy.eolssu.core.network

import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 웹 탭 사이의 로그인 회원 따르기([SignedInMember.follow])와 앱의 실행 범위([ProcessLaunch]) (S15P31E102-1264). */
class CrossTabSessionTest {

    /** 다른 탭과 함께 쓰는 저장소. 이 탭이 쓴 횟수를 센다. */
    private class SharedStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        var writes = 0

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            writes++
            values[key] = value
        }
    }

    private val parent = CoroutineScope(Job())

    @BeforeTest
    fun resetLaunch() = ProcessLaunch.reset()

    @AfterTest
    fun cancelScreens() {
        parent.cancel()
        ProcessLaunch.reset()
    }

    @Test
    fun followingAnotherTabStartsANewSessionWithoutWritingOrRunningOwnChangeListeners() {
        val store = SharedStore()
        val member = SignedInMember(store)
        member.set("1")
        val before = member.session.value
        val screen = member.screenScope(parent) {}
        val calls = mutableListOf<String>()
        member.onChange { calls += "every" }
        member.onChange(ownChangesOnly = true) { calls += "own" }
        store.values[SignedInMember.KEY] = "2" // 다른 탭이 저장소를 바꿨다
        val writes = store.writes

        member.follow("2")

        assertEquals("2", member.id)
        assertEquals(before.epoch + 1, member.session.value.epoch, "이전 계정의 진행 중 작업이 결과를 버리도록 세대를 올린다")
        assertTrue(screen.coroutineContext[Job]!!.isCancelled, "이전 계정 화면의 작업을 멈춘다")
        assertEquals(listOf("every"), calls, "저장소를 고치는 리스너는 부르지 않는다")
        assertEquals(writes, store.writes, "다른 탭이 이미 쓴 값을 다시 쓰지 않는다")

        member.follow("2")
        assertEquals(listOf("every"), calls, "같은 회원이면 아무 일도 하지 않는다")

        member.set(null)
        assertEquals(listOf("every", "every", "own"), calls, "이 탭이 바꾸면 모든 리스너를 부른다")
    }

    @Test
    fun appLaunchAnswersOncePerProcess() {
        assertTrue(ProcessLaunch.claim())
        assertFalse(ProcessLaunch.claim(), "화면이 다시 만들어져도(회전 등) 다시 답하지 않는다")
    }
}

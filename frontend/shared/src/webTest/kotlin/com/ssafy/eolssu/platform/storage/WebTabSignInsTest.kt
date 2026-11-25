package com.ssafy.eolssu.platform.storage

import com.ssafy.eolssu.core.network.SignedInMember
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 다른 탭의 로그인 회원 변경 따르기(S15P31E102-1264). 같은 페이지에서 localStorage를 바꾸면 `storage` 이벤트가 오지 않으므로,
 * 다른 탭이 바꾼 것처럼 값을 바꾼 뒤 이벤트를 직접 보낸다. 새로고침은 횟수만 센다.
 */
class WebTabSignInsTest {
    private val otherKey = "eolssu.test.web-tab-sign-ins"

    @AfterTest
    fun clear() {
        localStorage.removeItem(SignedInMember.KEY)
        localStorage.removeItem(otherKey)
    }

    private fun otherTabChanged() {
        window.dispatchEvent(Event("storage"))
    }

    @Test
    fun anotherTabsLogoutOrNewAccountIsFollowedAndRestartsOnce() {
        localStorage.setItem(SignedInMember.KEY, "1")
        val store = WebKeyValueStore()
        val member = SignedInMember(store)
        var restarts = 0
        WebTabSignIns(store) { restarts++ }.follow(member)

        localStorage.setItem(otherKey, "x")
        otherTabChanged()
        assertEquals(0, restarts, "다른 키의 변경은 무시한다")

        localStorage.setItem(SignedInMember.KEY, "2")
        otherTabChanged()
        assertEquals("2", member.id)
        assertEquals(1, restarts)

        otherTabChanged()
        assertEquals(1, restarts, "이미 따른 회원이면 다시 열지 않는다")

        localStorage.setItem(SignedInMember.KEY, "")
        otherTabChanged()
        assertNull(member.id, "다른 탭의 로그아웃")
        assertEquals(2, restarts)
    }

    @Test
    fun clearedStorageCountsAsLogout() {
        localStorage.setItem(SignedInMember.KEY, "1")
        val store = WebKeyValueStore()
        val member = SignedInMember(store)
        var restarts = 0
        WebTabSignIns(store) { restarts++ }.follow(member)

        localStorage.removeItem(SignedInMember.KEY)
        otherTabChanged()

        assertNull(member.id)
        assertEquals(1, restarts)
    }
}

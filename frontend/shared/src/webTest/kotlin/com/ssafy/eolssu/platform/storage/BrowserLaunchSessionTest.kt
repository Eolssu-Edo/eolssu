package com.ssafy.eolssu.platform.storage

import kotlinx.browser.document
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 웹 자동 로그인 끔의 실행 범위(S15P31E102-1264). 표지 쿠키를 지우는 것으로 브라우저를 새로 연 상태를 흉내 낸다. */
class BrowserLaunchSessionTest {
    @BeforeTest
    @AfterTest
    fun closeBrowser() {
        document.cookie = "${BrowserLaunchSession.MARKER}=; path=/; max-age=0"
    }

    private fun hasMarker() = document.cookie.split(';').any { it.trim() == "${BrowserLaunchSession.MARKER}=1" }

    @Test
    fun firstPageOfABrowserSessionIsANewLaunchOnce() {
        val first = BrowserLaunchSession()
        assertTrue(hasMarker(), "로그인과 상관없이 만들 때 바로 표지를 쓴다")
        assertTrue(first.claim())
        assertFalse(first.claim(), "같은 페이지에서 다시 묻지 않는다")
    }

    @Test
    fun reloadsAndNewTabsInTheSameBrowserSessionAreNotNewLaunches() {
        BrowserLaunchSession()

        val reloaded = BrowserLaunchSession()
        val newTab = BrowserLaunchSession()

        assertFalse(reloaded.claim())
        assertFalse(newTab.claim())
    }
}

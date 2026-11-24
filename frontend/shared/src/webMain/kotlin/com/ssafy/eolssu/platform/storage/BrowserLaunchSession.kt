package com.ssafy.eolssu.platform.storage

import com.ssafy.eolssu.core.network.LaunchSession
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * 웹의 '이번 실행'은 브라우저 세션이다(S15P31E102-1264). 만료일 없는 표지 쿠키([MARKER])는 모든 탭이 함께 보고
 * 브라우저를 종료하면 사라지므로, 표지가 없던 페이지만 새 실행으로 본다. 새로고침·새 탭은 같은 실행이라 자동 로그인을 끈 로그인도 이어진다.
 *
 * 표지는 로그인 여부와 상관없이 만들 때 바로 쓴다. 로그인 회원이 있을 때만 묻는 [claim]에 맡기면, 로그아웃 상태로 연 페이지에서
 * 자동 로그인을 끄고 로그인한 뒤 새로고침할 때 표지가 없어 로그아웃된다. 그래서 웹 플랫폼 모듈이 페이지를 열 때마다 하나 만든다.
 * 표지는 로그인 정보가 아니며 서버는 읽지 않는다. 브라우저의 '이전 세션 복원' 설정은 세션 쿠키도 되살리므로 그때는 새 실행으로 보지 못한다.
 */
class BrowserLaunchSession : LaunchSession {
    private val newBrowserSession = !hasMarker()
    private var claimed = false

    init {
        writeMarker()
    }

    override fun claim(): Boolean {
        if (claimed) return false
        claimed = true
        return newBrowserSession
    }

    companion object {
        /** 브라우저 세션 표지 쿠키 이름. */
        const val MARKER = "eolssu_browser_session"
    }
}

private fun hasMarker(): Boolean = runCatching {
    document.cookie.split(';').any { it.trim().startsWith("${BrowserLaunchSession.MARKER}=") }
}.getOrDefault(false)

private fun writeMarker() {
    val secure = if (window.location.protocol == "https:") "; Secure" else ""
    runCatching { document.cookie = "${BrowserLaunchSession.MARKER}=1; path=/; SameSite=Strict$secure" }
}

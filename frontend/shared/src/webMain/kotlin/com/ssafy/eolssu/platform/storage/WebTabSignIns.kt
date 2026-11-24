package com.ssafy.eolssu.platform.storage

import com.ssafy.eolssu.core.network.OtherTabSignIns
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.navigation.skipNextUnsavedChangesWarning
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * 다른 탭이 로그인 회원을 바꾸면(로그아웃·다른 계정 로그인) 이 탭도 따른다(S15P31E102-1264).
 * 쿠키는 모든 탭이 함께 쓰므로, 따르지 않으면 이 탭은 이전 계정 화면에서 새 계정의 쿠키로 요청을 보낸다.
 * 회원이 바뀌면 진행 중인 작업을 버리고([SignedInMember.follow]) 곧바로 첫 화면부터 다시 연다(사용자 결정: 안내 없이 바로 새로고침).
 *
 * `storage` 이벤트는 값을 바꾼 탭에는 오지 않으므로 새로고침이 되풀이되지 않는다. 이벤트의 키·값을 읽지 않고 [store]를 다시 읽어
 * 다른 키의 변경은 무시하고 `localStorage.clear()`도 로그아웃으로 따른다.
 */
class WebTabSignIns(
    private val store: KeyValueStore,
    private val restart: () -> Unit = ::restartFromFirstScreen,
) : OtherTabSignIns {
    override fun follow(member: SignedInMember) {
        window.addEventListener("storage", { _: Event -> sync(member) })
    }

    internal fun sync(member: SignedInMember) {
        val current = store.getString(SignedInMember.KEY)?.ifEmpty { null }
        if (current == member.id) return
        member.follow(current)
        restart()
    }
}

/**
 * 주소의 화면(`#경로`)을 버리고 페이지를 다시 연다. 그대로 새로고침하면 이전 계정에서 보던 화면(기록 상세 등)을 새 계정으로 다시 열 수 있다.
 * `#`만 다른 주소로 이동하면 페이지를 다시 읽지 않지만, `#` 없는 주소로 바꾸면 다시 읽는다. 기록에 남기지 않도록 `replace`를 쓴다.
 * 저장하지 않은 변경의 떠나기 확인(S15P31E102-1265)은 이번 한 번 건너뛴다(사용자 결정: 안내 없이 바로 새로고침). 확인 창에서
 * '머무르기'를 고르면 이 탭은 이전 계정 화면인데 쿠키는 새 계정이라, 저장하면 새 계정에 들어간다.
 */
private fun restartFromFirstScreen() {
    skipNextUnsavedChangesWarning()
    window.location.replace(window.location.pathname)
}

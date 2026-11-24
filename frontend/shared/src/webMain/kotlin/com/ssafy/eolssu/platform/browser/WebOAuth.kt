package com.ssafy.eolssu.platform.browser

import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.storage.SecureStore
import kotlinx.browser.sessionStorage
import kotlinx.browser.window

/**
 * 웹 간편 로그인(`docs/specs/social-auth.md` 웹 간편 로그인). 제공자 화면을 같은 탭에서 연다.
 * 페이지를 떠나므로 웹 앱은 여기서 끝나고, 제공자가 돌려보낸 복귀 주소에서 다시 시작한다.
 * 지금 화면 기록을 제공자 화면으로 바꾼다(`replace`). 간편 로그인 화면은 열리면 바로 시작하므로, 기록에 남기면
 * 제공자 화면에서 브라우저 뒤로 가기를 눌렀을 때 그 화면이 다시 열려 곧바로 제공자 화면으로 돌아간다.
 * 바꿔 두면 뒤로 가기는 버튼을 누른 화면(로그인·마이페이지·탈퇴)으로 돌아간다.
 */
class WebExternalBrowser : ExternalBrowser {
    override fun open(url: String): Boolean = runCatching { window.location.replace(url) }.isSuccess
}

/**
 * 탭의 `sessionStorage`. 제공자 화면에 다녀오는 동안 간편 로그인 시도(제공자·목적·state·만료)만 남긴다.
 * 같은 탭에서만 보이고 탭을 닫으면 사라진다. 암호화하지 않으므로 로그인 쿠키 같은 값은 넣지 않는다.
 * 저장소를 막은 브라우저에서는 읽기는 값 없음으로 보고 쓰기는 버린다(시도는 메모리에만 남아 복귀 뒤 다시 시작한다).
 */
class TabSessionStore : SecureStore {
    override fun getString(key: String): String? = runCatching { sessionStorage.getItem(key) }.getOrNull()

    override fun putString(key: String, value: String) {
        runCatching { sessionStorage.setItem(key, value) }
    }

    override fun remove(key: String) {
        runCatching { sessionStorage.removeItem(key) }
    }
}

/**
 * `index.html`이 받아 둔 웹 복귀 주소를 한 번만 꺼낸다. 없으면 null.
 * `index.html`은 `/auth/oauth/<제공자>/callback` 주소를 이 키에 남기고 주소창을 `/`로 바꾼 뒤 웹 앱을 띄운다(인가 코드가 주소창·이력에 남지 않게).
 */
fun takeWebOAuthReturn(): String? = runCatching {
    sessionStorage.getItem(OAUTH_RETURN_KEY)?.also { sessionStorage.removeItem(OAUTH_RETURN_KEY) }
}.getOrNull()

/** `index.html`의 같은 이름 키와 같아야 한다. */
private const val OAUTH_RETURN_KEY = "eolssu.oauth_return"

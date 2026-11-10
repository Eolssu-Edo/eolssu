package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.storage.SecureStore

/**
 * 로그인 화면(AUTH-04)의 '아이디 기억하기'·'자동 로그인' 선택. 비밀번호와 로그인 쿠키·토큰은 여기 두지 않는다.
 *
 * - 아이디 기억하기: 로그인한 이메일을 [secure](Android Keystore·iOS Keychain)에 남긴다. 웹처럼 [secure]가 없으면 [store]에 둔다.
 *   로그아웃해도 남고, 체크를 풀고 로그인하면 지운다.
 * - 자동 로그인: 세션 자체는 지금처럼 서버 쿠키(액세스 30분·리프레시 14일, 401이면 갱신)로 유지한다.
 *   체크를 풀고 이메일로 로그인하면 '다음 실행에서 로그아웃' 표시만 [store]에 남기고, 다음 앱 실행 때 [endSessionOnLaunch]가 알려 주면
 *   일반 로그아웃(서버 sign-out·쿠키 삭제)을 한다. 로그인한 회원이 바뀌면(로그아웃·다른 로그인·세션 만료) 표시를 지워
 *   간편 로그인·가입처럼 체크가 없는 로그인은 지금처럼 유지된다.
 */
class LoginPreferences(
    private val store: KeyValueStore,
    private val secure: SecureStore?,
    private val member: SignedInMember? = null,
) {
    init {
        // 이메일 로그인은 회원이 바뀐 뒤 [saveEmailLogin]으로 표시를 다시 남긴다.
        member?.onChange { store.putString(END_SESSION_KEY, "") }
    }

    /** 기억한 이메일. 없으면 null. */
    val rememberedEmail: String?
        get() = (if (secure != null) secure.getString(EMAIL_KEY) else store.getString(EMAIL_KEY))?.takeIf { it.isNotBlank() }

    /** 마지막으로 고른 자동 로그인 체크. 고른 적이 없으면 지금까지처럼 로그인을 유지하는 true. */
    val keepSignedIn: Boolean get() = store.getString(KEEP_KEY) != "false"

    /** 이메일 로그인에 성공했을 때 고른 선택을 남긴다. 회원이 바뀐 뒤에 불러야 자동 로그인 표시가 지워지지 않는다. */
    fun saveEmailLogin(email: String, rememberEmail: Boolean, keepSignedIn: Boolean) {
        if (rememberEmail) putEmail(email.trim()) else forgetEmail()
        store.putString(KEEP_KEY, keepSignedIn.toString())
        store.putString(END_SESSION_KEY, if (keepSignedIn) "" else "true")
    }

    fun forgetEmail() = putEmail("")

    /**
     * 앱 실행마다 한 번, 지난 로그인이 자동 로그인 없이 끝났으면 true. 같은 프로세스에서 화면이 다시 만들어져도(태블릿 회전 등)
     * 다시 묻지 않도록 프로세스 안에서 한 번만 답한다. 로그인한 회원이 없으면 false.
     */
    fun endSessionOnLaunch(): Boolean {
        if (launchChecked) return false
        launchChecked = true
        return store.getString(END_SESSION_KEY) == "true" && member?.id != null
    }

    private fun putEmail(value: String) {
        if (secure != null) {
            if (value.isEmpty()) secure.remove(EMAIL_KEY) else secure.putString(EMAIL_KEY, value)
        } else {
            store.putString(EMAIL_KEY, value)
        }
    }

    internal companion object {
        const val EMAIL_KEY = "auth.remembered_email"
        const val KEEP_KEY = "auth.keep_signed_in"
        const val END_SESSION_KEY = "auth.end_session_on_launch"

        /** 이 프로세스에서 [endSessionOnLaunch]를 이미 물었는지. 테스트는 [resetLaunchCheck]로 되돌린다. */
        private var launchChecked = false

        internal fun resetLaunchCheck() {
            launchChecked = false
        }
    }
}

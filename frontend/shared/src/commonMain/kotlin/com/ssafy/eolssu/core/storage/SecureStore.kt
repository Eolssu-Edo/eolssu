package com.ssafy.eolssu.core.storage

/**
 * 기기에 암호화해 남기는 작은 문자열 저장소. 로그인 쿠키처럼 [KeyValueStore]에 둘 수 없는 민감한 값을 담는다.
 * 구현은 플랫폼마다 둔다(Android Keystore로 암호화한 `SharedPreferences`, iOS Keychain). 웹은 브라우저가 쿠키를 보관하므로 없다(간편 로그인 시도만 `OAuthAttemptStore` 이름으로 탭 저장소를 따로 둔다).
 * 읽지 못하는 값(복원한 백업처럼 열쇠가 바뀐 경우)은 없는 것으로 본다.
 */
interface SecureStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun remove(key: String)
}

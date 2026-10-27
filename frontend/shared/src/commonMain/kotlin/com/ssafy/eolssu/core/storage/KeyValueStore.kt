package com.ssafy.eolssu.core.storage

/**
 * 기기에 남는 작은 문자열 저장소. 앱을 다시 켜도 값이 남는다.
 * 구현은 플랫폼마다 둔다(Android `SharedPreferences`, iOS `NSUserDefaults`, Web `localStorage`).
 * 읽기·쓰기가 즉시 끝나는 작은 설정값만 담는다. 큰 데이터·민감한 값(토큰 등)은 넣지 않는다.
 */
interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)
}

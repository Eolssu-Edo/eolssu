package com.ssafy.eolssu.core.storage

/** 테스트용 메모리 보안 저장소. 같은 객체를 다시 넘기면 앱을 다시 켠 것처럼 값이 남아 있다. */
class FakeSecureStore : SecureStore {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

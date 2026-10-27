package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.storage.KeyValueStore

/**
 * 첫 실행 과정(약관 동의 → 마이크 안내 → 로그인·가입)을 마쳤는지 기기에 남긴다.
 * 마친 뒤에는 앱을 다시 켜면 홈에서 시작한다. 서버 계정·로그인 상태와는 별개다.
 */
class FirstRunRepository(private val store: KeyValueStore) {
    fun isCompleted(): Boolean = store.getString(KEY) == COMPLETED

    fun markCompleted() = store.putString(KEY, COMPLETED)

    private companion object {
        const val KEY = "first_run_completed"
        const val COMPLETED = "true"
    }
}

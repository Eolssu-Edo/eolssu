package com.ssafy.eolssu.platform.storage

import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlinx.browser.localStorage

/**
 * 브라우저 `localStorage`에 저장한다. JS·Wasm이 함께 쓴다. 같은 주소(origin)에서만 값이 보인다.
 * 사생활 보호 모드처럼 저장소를 막은 브라우저에서는 예외가 나므로, 읽기는 값 없음으로 보고 쓰기는 버린다.
 */
class WebKeyValueStore : KeyValueStore {
    override fun getString(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()

    override fun putString(key: String, value: String) {
        runCatching { localStorage.setItem(key, value) }
    }
}

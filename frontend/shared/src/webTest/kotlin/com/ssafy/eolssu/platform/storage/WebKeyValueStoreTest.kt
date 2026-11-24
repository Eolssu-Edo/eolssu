package com.ssafy.eolssu.platform.storage

import kotlinx.browser.localStorage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebKeyValueStoreTest {
    private val key = "eolssu.test.web-key-value-store"

    @AfterTest
    fun clear() {
        localStorage.removeItem(key)
    }

    @Test
    fun missingKeyReadsAsNull() {
        assertNull(WebKeyValueStore().getString(key))
    }

    @Test
    fun savedValueIsReadBackAndOverwritten() {
        val store = WebKeyValueStore()
        store.putString(key, "first")
        store.putString(key, "second")
        assertEquals("second", store.getString(key))
    }

    @Test
    fun valueIsSharedThroughLocalStorage() {
        WebKeyValueStore().putString(key, "kept")
        // 새로 연 페이지처럼 다른 객체에서도 같은 값을 읽는다.
        assertEquals("kept", localStorage.getItem(key))
        assertEquals("kept", WebKeyValueStore().getString(key))
    }
}

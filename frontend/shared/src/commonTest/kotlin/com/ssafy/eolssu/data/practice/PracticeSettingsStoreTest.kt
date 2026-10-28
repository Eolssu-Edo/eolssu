package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeSettingsStoreTest {

    @Test
    fun micIsNotGrantedUntilAllowed() {
        assertFalse(PracticeSettingsStore(FakeKeyValueStore()).micGranted.value)
    }

    @Test
    fun grantedMicIsKeptAfterRestart() {
        val store = FakeKeyValueStore()
        PracticeSettingsStore(store).grantMic()
        // 앱을 다시 켜면 같은 기기 저장소로 새로 만든다.
        assertTrue(PracticeSettingsStore(store).micGranted.value)
    }
}

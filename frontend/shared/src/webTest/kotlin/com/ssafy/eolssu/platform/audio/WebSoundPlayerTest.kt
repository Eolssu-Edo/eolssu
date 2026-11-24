package com.ssafy.eolssu.platform.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse

class WebSoundPlayerTest {
    @Test
    fun stopWithoutSoundDoesNothing() {
        val player = WebSoundPlayer()
        player.stop()
        player.stop()
    }

    @Test
    fun missingFileReportsFailureInsteadOfThrowing() = runTest {
        val player = WebSoundPlayer()
        // 브라우저의 'error' 이벤트·play() 거절을 실제 시간으로 기다린다(가상 시간으로는 오지 않는다).
        val started = withContext(Dispatchers.Default) { player.play("files/eolssu-test/missing.mp3") }
        assertFalse(started)
        player.stop()
    }
}

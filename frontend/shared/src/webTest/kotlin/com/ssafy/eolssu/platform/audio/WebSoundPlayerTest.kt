package com.ssafy.eolssu.platform.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebSoundPlayerTest {
    @Test
    fun stopWithoutSoundDoesNothing() {
        val player = WebSoundPlayer()
        player.stop()
        player.stop()
    }

    @Test
    fun unplayableSoundReportsFailureInsteadOfThrowing() = runTest {
        // 없는 파일 대신 해석할 수 없는 data 주소를 준다. 서버 요청이 없어 Karma의 404 경고가 다른 테스트에 붙지 않는다(S15P31E102-1285).
        var requested: String? = null
        val player = WebSoundPlayer { path -> requested = path; "data:audio/wav;base64,AAAA" }
        // 브라우저의 'error' 이벤트·play() 거절을 실제 시간으로 기다린다(가상 시간으로는 오지 않는다).
        val started = withContext(Dispatchers.Default) { player.play("files/sounds/tap.mp3") }
        assertFalse(started)
        assertEquals("files/sounds/tap.mp3", requested)
        player.stop()
    }
}

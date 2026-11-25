package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.network.ServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebJangdanSamplePlayerTest {
    @Test
    fun integrityIsTheBase64OfTheManifestHash() {
        // 빈 바이트의 SHA-256. SRI 명세 예시와 같은 값이다.
        assertEquals(
            "sha256-47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=",
            jangdanSampleIntegrity("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        )
    }

    @Test
    fun strikeWithoutTargetPlaysTheWholeSample() {
        assertEquals(JangdanHitWindow(17_640 / RATE, null), jangdanHitWindow(17_640, null))
    }

    @Test
    fun rollLongerThanItsBeatIsCutAndFadedForFiveMilliseconds() {
        val window = jangdanHitWindow(26_727, 22_050)
        assertEquals(22_050 / RATE, window.durationSeconds)
        assertEquals(JANGDAN_TRIM_FADE_FRAMES / RATE, window.fadeOutSeconds)
    }

    @Test
    fun rollShorterThanItsBeatIsNotStretched() {
        // 음높이를 바꾸지 않으므로 원본 길이만 틀고 남는 박자는 비운다.
        assertEquals(JangdanHitWindow(26_727 / RATE, null), jangdanHitWindow(26_727, 30_000))
    }

    @Test
    fun veryShortCutFadesHalfOfWhatIsLeft() {
        val window = jangdanHitWindow(26_727, 300)
        assertEquals(300 / RATE, window.durationSeconds)
        assertEquals(150 / RATE, window.fadeOutSeconds)
    }

    @Test
    fun nonPositiveTargetStillPlaysOneFrame() {
        assertEquals(1 / RATE, jangdanHitWindow(26_727, 0).durationSeconds)
    }

    @Test
    fun hitsAreRefusedUntilBeginAndAfterRelease() {
        val player = WebJangdanSamplePlayer(WebJangdanSampleCache(ServerAddress(UNREACHABLE)) { error("unused") }, WebAudioOutput())
        try {
            assertFalse(player.play(JangdanClip(JangdanSample.Deong), 1f))
            assertTrue(player.begin(accompanyingModel = false))
            player.release()
            assertFalse(player.play(JangdanClip(JangdanSample.Deong), 1f))
        } finally {
            player.release()
        }
    }

    @Test
    fun releasedPlayerCanBeginAgain() {
        // 연습 화면은 탭이 가려지거나 재생이 실패할 때마다 release()한 뒤 같은 플레이어로 다시 튼다.
        val output = WebAudioOutput()
        val player = WebJangdanSamplePlayer(WebJangdanSampleCache(ServerAddress(UNREACHABLE)) { error("unused") }, output)
        val other = WebJangdanSamplePlayer(WebJangdanSampleCache(ServerAddress(UNREACHABLE)) { error("unused") }, output)
        try {
            assertTrue(player.begin(accompanyingModel = false))
            player.release()
            player.release()
            other.release()
            assertTrue(player.begin(accompanyingModel = false))
            assertTrue(other.begin(accompanyingModel = true))
        } finally {
            player.release()
            other.release()
        }
    }

    @Test
    fun sampleMissingFromTheManifestFailsPrepare() = runTest {
        val player = WebJangdanSamplePlayer(WebJangdanSampleCache(ServerAddress(UNREACHABLE)) { MANIFEST.encodeToByteArray() }, WebAudioOutput())
        try {
            assertFalse(player.prepare(setOf(JangdanClip(JangdanSample.Gi))))
        } finally {
            player.release()
        }
    }

    @Test
    fun rejectedDownloadFailsPrepareInsteadOfThrowing() = runTest {
        val player = WebJangdanSamplePlayer(WebJangdanSampleCache(ServerAddress(UNREACHABLE)) { MANIFEST.encodeToByteArray() }, WebAudioOutput())
        try {
            // 브라우저가 막는 포트라 fetch Promise가 바로 거절된다. 거절은 실제 시간으로 기다린다.
            val ready = withContext(Dispatchers.Default) { player.prepare(setOf(JangdanClip(JangdanSample.Deong))) }
            assertFalse(ready)
            assertFalse(player.interrupted)
        } finally {
            player.release()
        }
    }

    private companion object {
        const val RATE = 44_100.0

        /** 브라우저가 연결하지 않는 포트(Fetch 명세의 bad port). 네트워크로 나가지 않는다. */
        const val UNREACHABLE = "http://127.0.0.1:1"

        const val MANIFEST = """{"assets":[{"file":"deong.m4a","sha256":"b1edcdfe91542cf6d7668a029a63f823d39c612aac62af1a6719823998e9a81c","bytes":6225,"frames":17640}]}"""
    }
}

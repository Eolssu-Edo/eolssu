package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.JangdanVolumeLimit
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.platform.audio.WebAudioRecorder
import com.ssafy.eolssu.platform.audio.WebRecordingFiles
import com.ssafy.eolssu.platform.permission.WebMicPermission
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.storage.WebKeyValueStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.browser.window
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 웹 플랫폼 모듈이 브라우저 구현을 넘기는지. 앱 모듈과 함께 만드는 검사는 [WebKoinResolutionTest]가 한다. */
class WebPlatformModuleTest {
    @Test
    fun serverIsThePageOriginAndStorageIsLocalStorage() {
        val app = koinApplication { modules(webPlatformModule()) }
        try {
            assertEquals(window.location.origin, app.koin.get<ServerAddress>().baseUrl)
            assertIs<WebKeyValueStore>(app.koin.get<KeyValueStore>())
        } finally {
            app.close()
        }
    }

    @Test
    fun recordingFeaturesUseTheBrowserImplementations() {
        val app = koinApplication { modules(webPlatformModule()) }
        try {
            assertIs<WebAudioRecorder>(app.koin.get<AudioRecorder>())
            assertIs<WebMicPermission>(app.koin.get<MicPermission>())
            assertIs<WebRecordingFiles>(app.koin.get<RecordingFiles>())
            // 브라우저는 기기 볼륨을 읽지 못해 평가 녹음 중 장단을 늘 약 1/10로 줄인다(S15P31E102-1262).
            assertEquals(0.1f, app.koin.get<JangdanVolumeLimit>().gain())
        } finally {
            app.close()
        }
    }

    @Test
    fun browserHttpEngineIsCreated() {
        val app = koinApplication { modules(webPlatformModule()) }
        try {
            app.koin.get<HttpClientEngine>().close()
        } finally {
            app.close()
        }
    }
}

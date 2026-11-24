package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.network.ServerAddress
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
    fun browserHttpEngineIsCreated() {
        val app = koinApplication { modules(webPlatformModule()) }
        try {
            app.koin.get<HttpClientEngine>().close()
        } finally {
            app.close()
        }
    }
}

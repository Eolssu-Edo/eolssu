package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.UnavailableAudioPlayer

import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.audio.WebSoundPlayer
import com.ssafy.eolssu.platform.storage.WebKeyValueStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.browser.window
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Web(JS·Wasm) 구현 등록. webMain의 `App(onNavHostReady)`가 넘긴다.
 * 서버는 페이지와 같은 주소(운영 Nginx가 `/api`를 백엔드로 넘긴다)이고, 로그인 쿠키는 브라우저가 보관한다.
 */
internal fun webPlatformModule(): Module = module {
    factory<AudioPlayer> { UnavailableAudioPlayer() }
    single<KeyValueStore> { WebKeyValueStore() }
    single<HttpClientEngine> { Js.create() }
    single { ServerAddress(window.location.origin) }
    single<SoundPlayer> { WebSoundPlayer() }
}

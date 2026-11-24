package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.JangdanVolumeLimit
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.UnavailableSheetAudioDeviceStore

import com.ssafy.eolssu.app.di.OAuthAttemptStore
import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.storage.SecureStore
import com.ssafy.eolssu.data.user.OAuthRoute
import com.ssafy.eolssu.data.user.WebOAuthCallback
import com.ssafy.eolssu.platform.audio.WebAudioOutput
import com.ssafy.eolssu.platform.audio.WebAudioPlayer
import com.ssafy.eolssu.platform.audio.WebJangdanSampleCache
import com.ssafy.eolssu.platform.audio.WebJangdanSamplePlayer
import com.ssafy.eolssu.platform.audio.WebAudioRecorder
import com.ssafy.eolssu.platform.audio.WebJangdanVolumeLimit
import com.ssafy.eolssu.platform.audio.WebRecordingFiles
import com.ssafy.eolssu.platform.audio.WebRecordingStore
import com.ssafy.eolssu.platform.audio.WebSoundPlayer
import com.ssafy.eolssu.platform.browser.TabSessionStore
import com.ssafy.eolssu.platform.browser.WebExternalBrowser
import com.ssafy.eolssu.platform.browser.takeWebOAuthReturn
import com.ssafy.eolssu.platform.permission.WebMicPermission
import com.ssafy.eolssu.platform.storage.WebKeyValueStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.browser.window
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Web(JS·Wasm) 구현 등록. webMain의 `App(onNavHostReady)`가 넘긴다.
 * 서버는 페이지와 같은 주소(운영 Nginx가 `/api`를 백엔드로 넘긴다)이고, 로그인 쿠키는 브라우저가 보관한다.
 * 간편 로그인은 같은 탭에서 제공자 화면으로 이동하고 같은 주소의 웹 복귀 경로([WebOAuthCallback])로 돌아온다.
 * 돌아온 페이지가 시작할 때 `index.html`이 받아 둔 복귀 주소를 [IncomingLinks]로 넘긴다.
 */
internal fun webPlatformModule(): Module = module {
    factory<AudioPlayer> { WebAudioPlayer() }
    single<SheetAudioDeviceStore> { UnavailableSheetAudioDeviceStore }
    single<KeyValueStore> { WebKeyValueStore() }
    single<HttpClientEngine> { Js.create() }
    single { ServerAddress(window.location.origin) }
    single<SoundPlayer> { WebSoundPlayer() }
    single<OAuthRoute> { WebOAuthCallback(window.location.origin) }
    single<ExternalBrowser> { WebExternalBrowser() }
    single { IncomingLinks().apply { takeWebOAuthReturn()?.let(::deliver) } }
    single<SecureStore>(OAuthAttemptStore) { TabSessionStore() }
    // 웹 장단(S15P31E102-1260). 평가 녹음 중 배율은 아래 WebJangdanVolumeLimit(1262)이 정한다.
    single { WebJangdanSampleCache(get()) }
    single { WebAudioOutput() }
    factory<JangdanSamplePlayer> { WebJangdanSamplePlayer(get(), get()) }
    // 웹 녹음과 연주 평가(S15P31E102-1262): 녹음은 16kHz WAV로 탭 메모리에 두고 서버가 m4a로 바꿔 저장한다.
    single { WebRecordingStore() }
    factory<AudioRecorder> { WebAudioRecorder(get()) }
    single<MicPermission> { WebMicPermission() }
    single<RecordingFiles> { WebRecordingFiles(get()) }
    factory<JangdanVolumeLimit> { WebJangdanVolumeLimit }
}

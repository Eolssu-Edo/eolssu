package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.FileSheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.JangdanSampleFiles
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.ModelAudioFileCache
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.storage.SecureStore
import com.ssafy.eolssu.data.user.IosDevOAuthRelay
import com.ssafy.eolssu.data.user.OAuthRoute
import com.ssafy.eolssu.platform.audio.IosAudioPlayer
import com.ssafy.eolssu.platform.audio.IosAudioRecorder
import com.ssafy.eolssu.platform.audio.IosJangdanSamplePlayer
import com.ssafy.eolssu.platform.audio.IosRecordingFiles
import com.ssafy.eolssu.platform.audio.IosSoundPlayer
import com.ssafy.eolssu.platform.audio.applicationSupportPath
import com.ssafy.eolssu.platform.browser.IosExternalBrowser
import com.ssafy.eolssu.platform.permission.IosMicPermission
import com.ssafy.eolssu.platform.storage.IosKeyValueStore
import com.ssafy.eolssu.platform.storage.IosSecureStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.files.Path
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.*

/**
 * iOS 구현 등록. `MainViewController`가 `App`에 넘긴다.
 * 간편 로그인은 시스템 인증 창과 서버 중계 복귀(`IosOAuthRelay`, 개발 앱은 `IosDevOAuthRelay`)를 쓴다. 복귀 주소는 인증 창이 [IncomingLinks]로 넘긴다.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosPlatformModule(): Module = module {
    single { IncomingLinks() }
    single<ExternalBrowser> { IosExternalBrowser(get()) }
    single<OAuthRoute> { IosDevOAuthRelay.forBundle(NSBundle.mainBundle.bundleIdentifier) }
    single<KeyValueStore> { IosKeyValueStore() }
    single<HttpClientEngine> { Darwin.create() }
    single { ServerAddress.Production }
    single<SecureStore> { IosSecureStore() }
    single { SecureCookiesStorage(get(), now = get()) }
    single<MicPermission> { IosMicPermission() }
    // 녹음기·재생기는 화면마다 하나씩 쓰고 화면이 끝날 때 놓는다.
    factory<AudioRecorder> { IosAudioRecorder() }
    factory<AudioPlayer> { IosAudioPlayer() }
    single {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        val engine = Darwin.create {
            configureSession {
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
                URLCache = null
            }
        }
        ModelAudioFileCache(Path(caches, "model-audio-v1"), engine, Dispatchers.IO)
    }
    single<SheetAudioDeviceStore> {
        val directory = Path(applicationSupportPath(), "sheet-audio-v1")
        val engine = Darwin.create {
            configureSession {
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
                URLCache = null
            }
        }
        FileSheetAudioDeviceStore(directory, engine, Dispatchers.IO) {
            check(NSURL.fileURLWithPath(directory.toString())
                .setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null))
        }
    }
    // 검수 장단 M4A는 다시 받을 수 있으므로 시스템이 비울 수 있는 Caches에 둔다.
    single {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        JangdanSampleFiles(Path(caches, "jangdan-practice-v1"), get(), Dispatchers.IO)
    }
    factory<JangdanSamplePlayer> { IosJangdanSamplePlayer(get(), Dispatchers.IO) }
    single<RecordingFiles> { IosRecordingFiles() }
    single<SoundPlayer> { IosSoundPlayer() }
}

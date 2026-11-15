package com.ssafy.eolssu.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.NetworkSecurityPolicy
import com.ssafy.eolssu.core.audio.FileSheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.ModelAudioFileCache
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.JangdanSamplePlayer
import com.ssafy.eolssu.core.audio.JangdanVolumeLimit
import com.ssafy.eolssu.platform.audio.AndroidJangdanSamplePlayer
import com.ssafy.eolssu.platform.audio.AndroidJangdanVolumeLimit
import com.ssafy.eolssu.platform.audio.JangdanSampleCache
import java.io.File
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.platform.audio.AndroidAudioPlayer
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.browser.ExternalBrowser
import com.ssafy.eolssu.core.browser.IncomingLinks
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.storage.SecureStore
import com.ssafy.eolssu.platform.audio.AndroidAudioRecorder
import com.ssafy.eolssu.platform.audio.AndroidRecordingFiles
import com.ssafy.eolssu.platform.audio.AndroidSoundPlayer
import com.ssafy.eolssu.platform.browser.AndroidExternalBrowser
import com.ssafy.eolssu.platform.permission.AndroidMicPermission
import com.ssafy.eolssu.platform.storage.AndroidKeyValueStore
import com.ssafy.eolssu.platform.storage.AndroidSecureStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android 구현 등록. `MainActivity`가 넘긴 Context로 만든다(`App(context)`).
 * [incomingLinks]는 `MainActivity`가 App Links 복귀 주소를 넣는 곳이다.
 */
internal fun androidPlatformModule(context: Context, serverAddress: ServerAddress, incomingLinks: IncomingLinks): Module {
    val appContext = context.applicationContext
    return module {
        single<KeyValueStore> { AndroidKeyValueStore(appContext) }
        single<HttpClientEngine> { OkHttp.create() }
        single { serverAddress }
        // 로그인 쿠키와 간편 로그인 시도 상태(OAuthAttempts)를 암호화해 남긴다.
        single<SecureStore> { AndroidSecureStore(appContext) }
        single { SecureCookiesStorage(get(), now = get()) }
        single<ExternalBrowser> { AndroidExternalBrowser(appContext) }
        single { incomingLinks }
        single<MicPermission> { AndroidMicPermission.of(appContext) }
        // 연습 화면마다 따로 녹음한다.
        factory<AudioRecorder> { AndroidAudioRecorder(appContext) }
        factory<AudioPlayer> { AndroidAudioPlayer(appContext) }
        single {
            ModelAudioFileCache(
                Path(appContext.cacheDir.absolutePath, "model-audio-v1"), get(), Dispatchers.IO,
                allowDebugLoopback = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                    NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"),
            )
        }
        single<SheetAudioDeviceStore> {
            FileSheetAudioDeviceStore(
                Path(appContext.noBackupFilesDir.absolutePath, "sheet-audio-v1"), get(), Dispatchers.IO,
                allowDebugLoopback = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                    NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"),
            )
        }
        single { JangdanSampleCache(File(appContext.cacheDir, "jangdan-practice-v1"), get()) }
        factory<JangdanSamplePlayer> { AndroidJangdanSamplePlayer(appContext, get()) }
        // 평가 녹음 중 장단 크기를 시스템 볼륨으로 압축한다(S15P31E102-878).
        factory<JangdanVolumeLimit> { AndroidJangdanVolumeLimit(appContext) }
        // 업로드를 기다리는 녹음. RecordingUploads가 쓴다.
        single<RecordingFiles> { AndroidRecordingFiles(appContext) }
        single<SoundPlayer> { AndroidSoundPlayer() }
    }
}

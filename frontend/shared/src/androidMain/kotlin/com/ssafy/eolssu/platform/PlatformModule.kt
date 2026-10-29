package com.ssafy.eolssu.platform

import android.content.Context
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.audio.AndroidAudioRecorder
import com.ssafy.eolssu.platform.audio.AndroidRecordingFiles
import com.ssafy.eolssu.platform.permission.AndroidMicPermission
import com.ssafy.eolssu.platform.storage.AndroidKeyValueStore
import com.ssafy.eolssu.platform.storage.AndroidSecureStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android 구현 등록. `MainActivity`가 넘긴 Context로 만든다(`App(context)`). */
internal fun androidPlatformModule(context: Context): Module {
    val appContext = context.applicationContext
    return module {
        single<KeyValueStore> { AndroidKeyValueStore(appContext) }
        single<HttpClientEngine> { OkHttp.create() }
        single { ServerAddress.Production }
        single { SecureCookiesStorage(AndroidSecureStore(appContext), now = get()) }
        single<MicPermission> { AndroidMicPermission.of(appContext) }
        // 연습 화면마다 따로 녹음한다.
        factory<AudioRecorder> { AndroidAudioRecorder(appContext) }
        // 업로드를 기다리는 녹음. 실제 업로드가 붙으면 RecordingUploads가 쓴다(S15P31E102-187).
        single<RecordingFiles> { AndroidRecordingFiles(appContext) }
    }
}

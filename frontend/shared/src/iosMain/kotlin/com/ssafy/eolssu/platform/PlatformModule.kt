package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.MicPermission
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.storage.SecureStore
import com.ssafy.eolssu.platform.audio.IosAudioPlayer
import com.ssafy.eolssu.platform.audio.IosAudioRecorder
import com.ssafy.eolssu.platform.audio.IosRecordingFiles
import com.ssafy.eolssu.platform.audio.IosSoundPlayer
import com.ssafy.eolssu.platform.permission.IosMicPermission
import com.ssafy.eolssu.platform.storage.IosKeyValueStore
import com.ssafy.eolssu.platform.storage.IosSecureStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS 구현 등록. `MainViewController`가 `App`에 넘긴다. 간편 로그인(외부 브라우저·복귀 주소)은 아직 없다. */
internal fun iosPlatformModule(): Module = module {
    single<KeyValueStore> { IosKeyValueStore() }
    single<HttpClientEngine> { Darwin.create() }
    single { ServerAddress.Production }
    single<SecureStore> { IosSecureStore() }
    single { SecureCookiesStorage(get(), now = get()) }
    single<MicPermission> { IosMicPermission() }
    // 녹음기·재생기는 화면마다 하나씩 쓰고 화면이 끝날 때 놓는다.
    factory<AudioRecorder> { IosAudioRecorder() }
    factory<AudioPlayer> { IosAudioPlayer() }
    single<RecordingFiles> { IosRecordingFiles() }
    single<SoundPlayer> { IosSoundPlayer() }
}

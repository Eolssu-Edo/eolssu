package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.UnavailableAudioPlayer

import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.network.SecureCookiesStorage
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.audio.IosSoundPlayer
import com.ssafy.eolssu.platform.storage.IosKeyValueStore
import com.ssafy.eolssu.platform.storage.IosSecureStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS 구현 등록. `MainViewController`가 `App`에 넘긴다. */
internal fun iosPlatformModule(): Module = module {
    factory<AudioPlayer> { UnavailableAudioPlayer() }
    single<KeyValueStore> { IosKeyValueStore() }
    single<HttpClientEngine> { Darwin.create() }
    single { ServerAddress.Production }
    single { SecureCookiesStorage(IosSecureStore(), now = get()) }
    single<SoundPlayer> { IosSoundPlayer() }
}

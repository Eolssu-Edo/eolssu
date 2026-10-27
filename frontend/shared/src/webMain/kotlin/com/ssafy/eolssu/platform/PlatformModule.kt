package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.storage.WebKeyValueStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Web(JS·Wasm) 구현 등록. webMain의 `App(onNavHostReady)`가 넘긴다. */
internal fun webPlatformModule(): Module = module {
    single<KeyValueStore> { WebKeyValueStore() }
}

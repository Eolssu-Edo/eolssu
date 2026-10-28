package com.ssafy.eolssu.platform

import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.storage.IosKeyValueStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS 구현 등록. `MainViewController`가 `App`에 넘긴다. */
internal fun iosPlatformModule(): Module = module {
    single<KeyValueStore> { IosKeyValueStore() }
}

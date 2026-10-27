package com.ssafy.eolssu.platform

import android.content.Context
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.platform.storage.AndroidKeyValueStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android 구현 등록. `MainActivity`가 넘긴 Context로 만든다(`App(context)`). */
internal fun androidPlatformModule(context: Context): Module {
    val appContext = context.applicationContext
    return module {
        single<KeyValueStore> { AndroidKeyValueStore(appContext) }
    }
}

package com.ssafy.eolssu.platform.storage

import android.content.Context
import com.ssafy.eolssu.core.storage.KeyValueStore

/** `SharedPreferences`(파일 이름 `eolssu`)에 저장한다. 쓰기는 `apply()`로 백그라운드에서 디스크에 남긴다. */
class AndroidKeyValueStore(context: Context) : KeyValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    private companion object {
        const val FILE_NAME = "eolssu"
    }
}

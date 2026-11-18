package com.ssafy.eolssu.platform.storage

import com.ssafy.eolssu.core.storage.KeyValueStore
import platform.Foundation.NSUserDefaults

/** 앱 기본 `NSUserDefaults`에 저장한다. */
class IosKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStore {
    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}

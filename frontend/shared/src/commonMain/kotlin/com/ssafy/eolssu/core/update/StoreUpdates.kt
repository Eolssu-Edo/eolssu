package com.ssafy.eolssu.core.update

import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * 스토어(Play 스토어·App Store)에 올라온 더 새 버전을 확인하고 그 업데이트 화면을 여는 플랫폼 구현.
 * Android(`AndroidStoreUpdates`)·iOS(`IosStoreUpdates`)만 등록한다. Web은 등록하지 않고,
 * 스토어에서 설치하지 않은 앱(QA APK, Ad Hoc IPA, TestFlight)에서는 [latest]가 null을 돌려준다(S15P31E102-1237).
 */
interface StoreUpdates {
    /** 설치된 앱보다 새 스토어 버전. 없거나 확인할 수 없으면 null이다. */
    suspend fun latest(): StoreUpdate?

    /** [update]의 스토어 업데이트 화면을 연다. */
    fun open(update: StoreUpdate)
}

/** 스토어의 새 버전. [version]은 같은 버전인지 가리는 값이다(Android `versionCode`, iOS 버전 문자열). */
data class StoreUpdate(val version: String)

/**
 * 앱을 켤 때 띄울 선택 업데이트 안내를 고른다. '나중에'를 누른 스토어 버전은 다시 묻지 않고,
 * 그보다 새 버전이 스토어에 올라오면 다시 안내한다.
 */
class StoreUpdateCheck(private val updates: StoreUpdates, private val store: KeyValueStore) {
    /** 안내할 새 버전. 없거나, '나중에'를 고른 버전이거나, 확인에 실패하면 null이다. */
    suspend fun pending(): StoreUpdate? {
        val update = try {
            updates.latest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 스토어 확인은 앱 사용을 막지 않는다. 실패하면 안내 없이 평소대로 시작한다.
            null
        } ?: return null
        return update.takeIf { it.version != store.getString(LATER_KEY) }
    }

    /** '나중에': 이 버전은 다시 묻지 않는다. */
    fun later(update: StoreUpdate) {
        store.putString(LATER_KEY, update.version)
    }

    fun open(update: StoreUpdate) {
        updates.open(update)
    }

    private companion object {
        const val LATER_KEY = "store_update_later_version"
    }
}

/**
 * 점으로 나눈 숫자 버전에서 [candidate]가 [installed]보다 새로우면 true(`1.0.1` > `1.0`, `1.10` > `1.9`).
 * 숫자가 아닌 부분이 있으면 비교할 수 없으므로 false다.
 */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    val new = versionParts(candidate) ?: return false
    val old = versionParts(installed) ?: return false
    for (i in 0 until maxOf(new.size, old.size)) {
        val a = new.getOrElse(i) { 0 }
        val b = old.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun versionParts(version: String): List<Int>? =
    version.trim().split('.').map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }

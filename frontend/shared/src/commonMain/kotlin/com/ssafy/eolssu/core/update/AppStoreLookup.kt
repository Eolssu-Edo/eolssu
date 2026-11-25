package com.ssafy.eolssu.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * App Store에 올라온 앱의 현재 버전과 앱 페이지 주소.
 * [id]는 App Store 앱 번호(`trackId`)로, 앱 안 App Store 시트(`SKStoreProductViewController`)를 열 때 쓴다(S15P31E102-1257).
 */
data class AppStoreRelease(val version: String, val url: String, val id: Long? = null)

/**
 * iTunes Lookup API(`https://itunes.apple.com/lookup?bundleId=…&country=kr`) 주소.
 * 앱은 한국 App Store에 있으므로 `country=kr`로 묻는다. 국가를 빼면 미국 스토어를 기준으로 찾는다.
 */
fun appStoreLookupUrl(bundleId: String): String = "https://itunes.apple.com/lookup?bundleId=$bundleId&country=kr"

/** iTunes Lookup 응답에서 첫 결과의 버전과 앱 페이지 주소, 앱 번호를 읽는다. 결과가 없거나 형식이 다르면 null이다. */
fun parseAppStoreLookup(body: String): AppStoreRelease? {
    val response = try {
        lookupJson.decodeFromString<LookupResponse>(body)
    } catch (e: SerializationException) {
        return null
    } catch (e: IllegalArgumentException) {
        return null
    }
    val result = response.results.firstOrNull() ?: return null
    val version = result.version?.takeIf { it.isNotBlank() } ?: return null
    val url = result.trackViewUrl?.takeIf { it.startsWith("https://") } ?: return null
    return AppStoreRelease(version, url, result.trackId?.takeIf { it > 0 })
}

private val lookupJson = Json { ignoreUnknownKeys = true }

@Serializable
private class LookupResponse(val results: List<LookupResult> = emptyList())

@Serializable
private class LookupResult(val version: String? = null, val trackViewUrl: String? = null, val trackId: Long? = null)

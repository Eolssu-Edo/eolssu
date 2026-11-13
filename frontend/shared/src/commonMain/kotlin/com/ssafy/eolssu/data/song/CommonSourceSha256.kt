package com.ssafy.eolssu.data.song

import io.ktor.util.sha256
import kotlinx.serialization.json.*

/** 서버와 같은 객체 키 정렬. DTO 기본값을 보충하기 전의 원문 JSON을 해시한다. */
internal fun commonSourceSha256(value: JsonElement): String {
    fun canonical(item: JsonElement): JsonElement = when (item) {
        is JsonObject -> JsonObject(item.entries.sortedBy { it.key }.associate { it.key to canonical(it.value) })
        is JsonArray -> JsonArray(item.map(::canonical))
        else -> item
    }
    return sha256(canonical(value).toString().encodeToByteArray()).toHexString()
}
